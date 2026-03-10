UPDATE ingestion_pipeline_entity
SET json = (json::jsonb #- '{sourceConfig,config,threadCount}')::json
WHERE pipelineType = 'profiler'
  AND (json->'sourceConfig'->'config'->>'threadCount')::numeric = 5;
