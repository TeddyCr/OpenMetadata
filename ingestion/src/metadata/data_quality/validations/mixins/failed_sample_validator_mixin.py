#  Copyright 2025 Collate
#  Licensed under the Collate Community License, Version 1.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#  https://github.com/open-metadata/OpenMetadata/blob/main/ingestion/LICENSE
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

"""
Mixin that orchestrates failed row sampling for test case validators.

When a test case has computePassedFailedRowCount=True and the result is Failed,
this mixin fetches a sample of failed rows and attaches it (along with the
inspection query for SQL sources) to the TestCaseResult via the
TestCaseResultResponse wrapper.
"""

import traceback
from abc import ABC, abstractmethod
from typing import Optional

from metadata.data_quality.api.models import TestCaseResultResponse
from metadata.generated.schema.entity.data.table import TableData
from metadata.generated.schema.tests.basic import TestCaseResult, TestCaseStatus
from metadata.generated.schema.tests.testCase import TestCase
from metadata.utils.logger import test_suite_logger

logger = test_suite_logger()


class FailedSampleValidatorMixin(ABC):
    """ABC mixin providing failed row sampling orchestration.

    Concrete validators must implement:
      - fetch_failed_rows_sample() -> TableData
      - filter() -> filter expression (dict for SQA, string for Pandas)
    """

    def get_inspection_query(self) -> Optional[str]:
        return getattr(self, "_inspection_query", None)

    @abstractmethod
    def fetch_failed_rows_sample(self) -> TableData:
        raise NotImplementedError

    def get_failed_sample_data(
        self, test_case: TestCase, result: TestCaseResult
    ) -> TestCaseResultResponse:
        """Build a TestCaseResultResponse, attaching failed row sample if applicable.

        Only fetches samples when:
          - test_case.computePassedFailedRowCount is True
          - result.testCaseStatus is Failed

        Args:
            test_case: The test case being validated
            result: The test case result from run_validation

        Returns:
            TestCaseResultResponse with optional failedRowsSample and inspectionQuery
        """
        response = TestCaseResultResponse(
            testCaseResult=result,
            testCase=test_case,
        )

        if not (
            getattr(test_case, "computePassedFailedRowCount", False)
            and result.testCaseStatus == TestCaseStatus.Failed
        ):
            return response

        try:
            failed_rows = self.fetch_failed_rows_sample()
            if failed_rows is not None:
                response.failedRowsSample = failed_rows
        except Exception:
            logger.debug(traceback.format_exc())
            logger.error("Failed to fetch failed rows sample")

        try:
            inspection_query = self.get_inspection_query()
            if inspection_query is not None:
                response.inspectionQuery = inspection_query
        except Exception:
            logger.debug(traceback.format_exc())
            logger.error("Failed to get inspection query")

        return response
