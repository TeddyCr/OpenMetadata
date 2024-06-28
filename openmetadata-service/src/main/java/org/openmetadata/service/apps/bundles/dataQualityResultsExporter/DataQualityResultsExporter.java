package org.openmetadata.service.apps.bundles.dataQualityResultsExporter;

import lombok.extern.slf4j.Slf4j;
import org.openmetadata.schema.entity.applications.configuration.internal.DataQualityResultsExporterConfig;
import org.openmetadata.schema.entity.applications.configuration.internal.connections.SnowflakeConnection;
import org.openmetadata.service.apps.AbstractNativeApplication;
import org.openmetadata.service.jdbi3.CollectionDAO;
import org.openmetadata.service.search.SearchRepository;
import org.openmetadata.schema.entity.app.App;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

@Slf4j
public class DataQualityResultsExporter extends AbstractNativeApplication {
    String jdbcUrl;
    Properties properties;

    public DataQualityResultsExporter(CollectionDAO collectionDAO, SearchRepository searchRepository) {
        super(collectionDAO, searchRepository);
    }

    @Override
    public void init(App app) {
        super.init(app);
        // init jdbc connection
        DataQualityResultsExporterConfig appConfig = (DataQualityResultsExporterConfig) app.getAppConfiguration();
        SnowflakeConnection config = (SnowflakeConnection) appConfig.getConfig();
        String jdbcUrl = String.format("jdbc:snowflake://%s.snowflakecomputing.com/",config.getAccount());
        Properties properties = new Properties();
        properties.put("user", config.getUsername());
        properties.put("password", config.getPassword());
        properties.put("warehouse", config.getWarehouse());
        properties.put("db", config.getDatabase());
        properties.put("role", config.getRole());
        this.properties = properties;
        this.jdbcUrl = jdbcUrl;
//        try {
//            Connection connection = DriverManager.getConnection(jdbcUrl, properties);
//        } catch (Exception e) {
//            LOG.error("Error connecting to Snowflake", e);
//        }
    }
}
