package org.openmetadata.service.apps.bundles.dataQualityResultsExporter;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.openmetadata.schema.entity.applications.configuration.internal.DataQualityResultsExporterConfig;
import org.openmetadata.schema.entity.applications.configuration.internal.connections.SnowflakeConnection;
import org.openmetadata.service.apps.AbstractNativeApplication;
import org.openmetadata.service.jdbi3.CollectionDAO;
import org.openmetadata.service.search.SearchRepository;
import org.openmetadata.schema.entity.app.App;
import org.quartz.JobExecutionContext;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

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
                "(testCaseId STRING, testCaseName STRING, testCaseType STRING, " +
                "testCaseEntityType STRING, testCaseParameters ARRAY, timestamp TIMESTAMP, " +
                "EntityLink STRING, status STRING, failedRows BIGINT, passedRows BIGINT, results ARRAY);";
        createStmt = String.format(createStmt, database, schema, table);
        connection.createStatement().execute(createStmt);
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
        super.startApp(jobExecutionContext);
    }
}
