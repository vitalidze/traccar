/*
 * Copyright 2012 - 2014 Anton Tananaev (anton.tananaev@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.model;

import java.io.File;
import java.io.StringReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.*;
import java.util.*;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.traccar.helper.AdvancedConnection;
import org.traccar.helper.DriverDelegate;
import org.traccar.helper.Log;
import org.traccar.helper.NamedParameterStatement;
import org.xml.sax.InputSource;

/**
 * Database abstraction class
 */
public class DatabaseDataManager implements DataManager {

    public DatabaseDataManager(Properties properties) throws Exception {
        initDatabase(properties);
    }

    /**
     * Database statements
     */
    private NamedParameterStatement queryGetDevices;
    private NamedParameterStatement queryAddPosition;
    private NamedParameterStatement queryUpdateLatestPosition;

    /**
     * Initialize database
     */
    private void initDatabase(Properties properties) throws Exception {

        // Load driver
        String driver = properties.getProperty("database.driver");
        if (driver != null) {
            String driverFile = properties.getProperty("database.driverFile");

            if (driverFile != null) {
                URL url = new URL("jar:file:" + new File(driverFile).getAbsolutePath() + "!/");
                URLClassLoader cl = new URLClassLoader(new URL[]{url});
                Driver d = (Driver) Class.forName(driver, true, cl).newInstance();
                DriverManager.registerDriver(new DriverDelegate(d));
            } else {
                Class.forName(driver);
            }
        }

        // Connect database
        String url = properties.getProperty("database.url");
        String user = properties.getProperty("database.user");
        String password = properties.getProperty("database.password");
        AdvancedConnection connection = new AdvancedConnection(url, user, password);

        // Load statements from configuration
        String query;

        query = properties.getProperty("database.selectDevice");
        if (query != null) {
            queryGetDevices = new NamedParameterStatement(connection, query);
        }

        query = properties.getProperty("database.insertPosition");
        if (query != null) {
            queryAddPosition = new NamedParameterStatement(connection, Statement.RETURN_GENERATED_KEYS, query);
        }

        query = properties.getProperty("database.updateLatestPosition");
        if (query != null) {
            queryUpdateLatestPosition = new NamedParameterStatement(connection, query);
        }
    }

    @Override
    public synchronized List<Device> getDevices() throws SQLException {

        List<Device> deviceList = new LinkedList<Device>();

        if (queryGetDevices != null) {
            queryGetDevices.prepare();
            ResultSet result = queryGetDevices.executeQuery();
            while (result.next()) {
                Device device = new Device();
                device.setId(result.getLong("id"));
                device.setImei(result.getString("imei"));
                deviceList.add(device);
            }
        }

        return deviceList;
    }

    /**
     * Devices cache
     */
    private Map<String, Device> devices;

    @Override
    public Device getDeviceByImei(String imei) throws SQLException {

        if (devices == null || !devices.containsKey(imei)) {
            devices = new HashMap<String, Device>();
            for (Device device : getDevices()) {
                devices.put(device.getImei(), device);
            }
        }

        return devices.get(imei);
    }

    @Override
    public synchronized Long addPosition(Position position) throws SQLException {

        if (queryAddPosition != null) {
            queryAddPosition.prepare();

            queryAddPosition = assignVariables(queryAddPosition, position);

            queryAddPosition.executeUpdate();

            ResultSet result = queryAddPosition.getGeneratedKeys();
            if (result != null && result.next()) {
                return result.getLong(1);
            }
        }

        return null;
    }

    @Override
    public void updateLatestPosition(Position position, Long positionId) throws SQLException {
        if (queryUpdateLatestPosition != null) {
            queryUpdateLatestPosition.prepare();
            
            queryUpdateLatestPosition = assignVariables(queryUpdateLatestPosition, position);
            queryUpdateLatestPosition.setLong("id", positionId);
            
            queryUpdateLatestPosition.executeUpdate();
        }
    }

    private NamedParameterStatement assignVariables(NamedParameterStatement preparedStatement, Position position) throws SQLException {

        preparedStatement.setLong("device_id", position.getDeviceId());
        preparedStatement.setTimestamp("time", position.getTime());
        preparedStatement.setBoolean("valid", position.getValid());
        preparedStatement.setDouble("altitude", position.getAltitude());
        preparedStatement.setDouble("latitude", position.getLatitude());
        preparedStatement.setDouble("longitude", position.getLongitude());
        preparedStatement.setDouble("speed", position.getSpeed());
        preparedStatement.setDouble("course", position.getCourse());
        preparedStatement.setString("address", position.getAddress());
        preparedStatement.setString("extended_info", position.getExtendedInfo());

        // DELME: Temporary compatibility support
        XPath xpath = XPathFactory.newInstance().newXPath();
        try {
            InputSource source = new InputSource(new StringReader(position.getExtendedInfo()));
            String index = xpath.evaluate("/info/index", source);
            if (!index.isEmpty()) {
                preparedStatement.setLong("id", Long.valueOf(index));
            } else {
                preparedStatement.setLong("id", null);
            }
            source = new InputSource(new StringReader(position.getExtendedInfo()));
            String power = xpath.evaluate("/info/power", source);
            if (!power.isEmpty()) {
                preparedStatement.setDouble("power", Double.valueOf(power));
            } else {
                preparedStatement.setLong("power", null);
            }
        } catch (XPathExpressionException e) {
            Log.warning("Error in XML: " + position.getExtendedInfo(), e);
            preparedStatement.setLong("id", null);
            preparedStatement.setLong("power", null);
        }

        return preparedStatement;

    }

}
