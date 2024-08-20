//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVAC_OPTIONS -parameters
//DEPS io.quarkus.platform:quarkus-bom:2.16.2.Final@pom
//DEPS io.quarkus:quarkus-picocli
//DEPS io.quarkus:quarkus-jdbc-postgresql
//DEPS com.googlecode.json-simple:json-simple:1.1.1

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;


import java.net.*;
import java.io.*;
import java.sql.*;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import java.util.Map;
import java.util.Set;

@Command(name = "crowsnestIntegrationStubJson")

public class crowsnestIntegrationStubJson implements Runnable {   

@Option(
   names = {"--hash"},
   description = "Hash from Crowsnet Toggle", required = true) 
public String integrationHash;

String query = String.format("SELECT * from integrations WHERE hash = %s", integrationHash);

//@Parameters(paramLabel = "<dbUsername>", defaultValue = "telescope", description = "Db username")
    //String userName;
//    String userName = System.getenv("PG_USER");
    String userName = "postgres";

    //@Parameters(paramLabel = "<dbPassword>", defaultValue = "quarkus", description = "Db password")
    //String password;
//    String password = System.getenv("PG_PASSWORD");
    String password = "qwas1234";

    //@Parameters(paramLabel = "<dbUrl>", defaultValue = "jdbc:postgresql://postgresql:5432/telescope", description = "Db URL")
    //String url;
    //String url = System.getenv("PG_DB");
    String url = "jdbc:postgresql://localhost:5432/crowsnest";
    Boolean verbose = true;
    Integer integration_id;
    Integer capability_id = 0;
    Integer success_criteria;
    Integer success, failure = 0;
    Integer flag_id = 1;
    String endpoint;

    /**
     * Update the capability table with the new flag_id (1 = red, 2 = green)
     * 
     * @return the number of affected rows
     */
    public int setCapabilityWithFlag() {

        String query = "UPDATE capability "
                + "SET flag_id = ? "
                + "WHERE id = ?";

        int affectedrows = 0;
        try (Connection conn = DriverManager.getConnection(url, userName, password)) {

            PreparedStatement statement = conn.prepareStatement(query);
            statement.setInt(1, flag_id);
            statement.setInt(2, capability_id);
            affectedrows = statement.executeUpdate();

        } catch (SQLException ex) {
            System.out.println(ex.getMessage());
        }
        return affectedrows;
    }

    /**
     * Update the integrations table with last_update
     * 
     * @return the number of affected rows
     */
    public int setIntegrationLastUpdate() {
        String query = "UPDATE integrations "
                + "SET last_update = Now() "
                + "WHERE integration_id = ?";

        int affectedrows = 0;

        try (Connection conn = DriverManager.getConnection(url, userName, password)) {

            PreparedStatement statement = conn.prepareStatement(query);
            statement.setInt(1, integration_id);

            affectedrows = statement.executeUpdate();

        } catch (SQLException ex) {
            System.out.println(ex.getMessage());
        }
        return affectedrows;
    }

    /**
     * Update the flag depending the score.
     * 
     * @param StringBuilder responseData
     * @throws ParseException
     */
    public void setFlagDependingScore(StringBuilder responseData) throws ParseException {

        success = 0;
        failure = 0;

        String jsonString = responseData.toString();

        JSONParser jsonParser = new JSONParser();
        JSONObject jsonObject = (JSONObject) jsonParser.parse(jsonString);
        String value = (String) jsonObject.get("numberResponse");

        
        Integer numberResponse = Integer.parseInt(value);
        System.out.printf("Returned Value: %d\n", numberResponse);
        System.out.printf("Success Criteria: %d\n", success_criteria);

        if (numberResponse == success_criteria) {    
            success++;
            flag_id = 2;
       } else {
            failure++;
            flag_id = 1;       
    }
}


    public void processData() {

        try (Connection conn = DriverManager.getConnection(url, userName, password)) {
            String newquery = "SELECT * from integrations WHERE hash = '" + integrationHash + "'";
//            System.out.printf("Query: %s\n",newquery);
            PreparedStatement statement = conn.prepareStatement(newquery);
            ResultSet rs = statement.executeQuery();
            {
                while (rs.next()) {
                    integration_id = rs.getInt("integration_id");
                    capability_id = rs.getInt("capability_id");
                    success_criteria = rs.getInt("success_criteria");
                    endpoint = rs.getString("url");
                }

                    //System.out.printf("Success Criteria: %s\n", success_criteria);

                if (endpoint != null) {

                    @SuppressWarnings("deprecation")
                    URL url = new URL(endpoint);
                    System.out.printf("Remote URL: %s\n", url);
                    HttpURLConnection con = (HttpURLConnection) url.openConnection();
                    con.setRequestMethod("GET");
                    con.setRequestProperty("Content-Type", "application/json");
                    con.setRequestProperty("Authorization", "Bearer ".concat(endpoint));
                    con.setRequestProperty("Accept", "application/json");

                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(con.getInputStream(), "utf-8"))) {
                        StringBuilder response = new StringBuilder();
                        String responseLine = null;
                        while ((responseLine = br.readLine()) != null) {
                            response.append(responseLine.trim());
                        }
                        br.close();
                        setFlagDependingScore(response);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {

        System.out.printf("Running compliance check for CrowsNest integration using hash " + integrationHash + " \n");

        processData();

        if (capability_id != 0) {
            setCapabilityWithFlag();
            setIntegrationLastUpdate();
            String flagColour = ((flag_id == 1) ? "Red" : "Green");
                    System.out.printf("Flag updated to: %s\n", flagColour);
        }
    }
}
