package org.openmetadata.service.apps.bundles.dataQualityResultsExporter;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.openmetadata.common.utils.CommonUtil;
import org.openmetadata.schema.entity.app.AppRunRecord;
import org.openmetadata.schema.entity.applications.configuration.internal.DataQualityResultsExporterConfig;
import org.openmetadata.schema.entity.applications.configuration.internal.connections.SnowflakeConnection;
import org.openmetadata.service.apps.AbstractNativeApplication;
import org.openmetadata.service.exception.AppException;
import org.openmetadata.service.jdbi3.CollectionDAO;
import org.openmetadata.service.search.SearchRepository;
import org.openmetadata.schema.entity.app.App;
import org.openmetadata.service.util.JsonUtils;
import org.quartz.JobExecutionContext;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.openmetadata.service.exception.AppException.APP_RUN_RECORD_NOT_FOUND;

@Slf4j
public class DataQualityResultsExporter extends AbstractNativeApplication {
    String jdbcUrl;
    Properties properties;
    String database;
    String schema;
    String table;

    public DataQualityResultsExporter(CollectionDAO collectionDAO, SearchRepository searchRepository) {
        super(collectionDAO, searchRepository);
    }

    @SneakyThrows
    private Connection connect() {
        Class.forName("net.snowflake.client.jdbc.SnowflakeDriver");
        Connection connection = null;
        connection = DriverManager.getConnection(jdbcUrl, properties);
        return connection;
    }

    @SneakyThrows
    private void createTable() {
        Connection connection = connect();
        String createStmt = "CREATE TABLE %s.%s.%s " +
                "(testCaseId VARCHAR(512), testCaseName VARCHAR(512), testCaseType VARCHAR(512), " +
                "testCaseEntityType VARCHAR(512), testCaseParameters ARRAY, timestamp TIMESTAMP, " +
                "EntityLink VARCHAR(512), status STRING, failedRows BIGINT, passedRows BIGINT, results ARRAY);";
        createStmt = String.format(createStmt, database, schema, table);
        connection.createStatement().execute(createStmt);
        connection.close();
    }

    @SneakyThrows
    private void insertRecords(List<CollectionDAO.DataQualityResultsForExportRecord> records) {
        Connection connection = connect();
        ObjectMapper mapper = new ObjectMapper();
//        String stmt = String.format(
//                "INSERT INTO %s.%s.%s" +
//                        "(testCaseId, testCaseName, testCaseType, testCaseEntityType, testCaseParameters, " +
//                        "timestamp, entityLink, status, failedRows, passedRows, results)" +
//                        " VALUES (?,?,?,?,?,?,?,?,?,?,?)",
//                database, schema, table);
        String stmt = String.format(
                "INSERT INTO %s.%s.%s" +
                        "(testCaseId, testCaseName, testCaseType, testCaseEntityType, " +
                        "timestamp, entityLink, status, failedRows, passedRows, results, testCaseParameters)" +
                        " SELECT ?,?,?,?,?,?,?,?,?,PARSE_JSON(?),PARSE_JSON(?)",
                database, schema, table);
        PreparedStatement preparedStatement = connection.prepareStatement(stmt);
        for (CollectionDAO.DataQualityResultsForExportRecord record : records) {
            String testCaseParameters = mapper.writeValueAsString(record.getTestCaseParameters());
            String results = mapper.writeValueAsString(record.getResults());

            Timestamp timestamp = new Timestamp(record.getTimestamp());
            preparedStatement.setString(1, record.getTestCaseId());
            preparedStatement.setString(2, record.getTestCaseName());
            preparedStatement.setString(3, record.getTestCaseType());
            preparedStatement.setString(4, record.getTestCaseEntityType());
            preparedStatement.setTimestamp(5, timestamp);
            preparedStatement.setString(6, record.getEntityLink());
            preparedStatement.setString(7, record.getStatus());
            preparedStatement.setInt(8, record.getFailedRows());
            preparedStatement.setInt(9, record.getPassedRows());
            preparedStatement.setString(10, testCaseParameters);
            preparedStatement.setString(11,  results);
            preparedStatement.addBatch();
        }
        preparedStatement.executeBatch();
        connection.close();
    }

    @Override
    public void init(App app) {
        super.init(app);
        ObjectMapper mapper = new ObjectMapper();
        DataQualityResultsExporterConfig appConfig = mapper.convertValue(app.getAppConfiguration(), DataQualityResultsExporterConfig.class);
        SnowflakeConnection config = mapper.convertValue(appConfig.getConfig(), SnowflakeConnection.class);
        String jdbcUrl = String.format("jdbc:snowflake://%s.snowflakecomputing.com/",config.getAccount());
        Properties properties = new Properties();
        properties.put("user", config.getUsername());
        properties.put("password", config.getPassword());
        properties.put("warehouse", config.getWarehouse());
        properties.put("db", config.getDatabase());
        properties.put("role", config.getRole());
        this.properties = properties;
        this.jdbcUrl = jdbcUrl;
        this.database = appConfig.getDatabaseName();
        this.schema = appConfig.getSchemaName();
        this.table = appConfig.getTableName();
    }

    @Override
    public void install() {
        super.install();
        createTable();
    }

    @Override
    public void startApp(JobExecutionContext jobExecutionContext) {
        String lastRunTimeCond = "TRUE";
        UUID appId = getApp().getId();
        List<String> jsons = collectionDAO.appExtensionTimeSeriesDao().listAppRunRecord(appId.toString(), 2,0);
        if (!CommonUtil.nullOrEmpty(jsons) && jsons.size() >= 2) {
            // Get the last run before the current one
            AppRunRecord appRunRecord = JsonUtils.readValue(jsons.get(1), AppRunRecord.class);
            lastRunTimeCond = String.format("timestamp > %s", appRunRecord.getTimestamp());
        }
        List<CollectionDAO.DataQualityResultsForExportRecord> records =
                collectionDAO.dataQualityDataTimeSeriesDao().getDataQualityResultsForExport(lastRunTimeCond);
        if (!records.isEmpty()) {
            insertRecords(records);
        }
    }
}
