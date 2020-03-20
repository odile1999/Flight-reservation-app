package flightapp;

import java.util.ArrayList;
import java.util.Arrays;
import java.io.*;
import java.sql.*;
import java.util.*;
import java.security.*;
import java.security.spec.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/**
 * Runs queries against a back-end database
 */
public class Query {
    // DB Connection
    private Connection conn;

    // Password hashing parameter constants
    private static final int HASH_STRENGTH = 65536;
    private static final int KEY_LENGTH = 128;

    // Canned queries
    private static final String BEGIN_TRANSACTION_SQL = "BEGIN TRANSACTION;";
    private static final String COMMIT_SQL = "COMMIT TRANSACTION";
    private static final String ROLLBACK_SQL = "ROLLBACK TRANSACTION";
    private Statement generalStatement;

    private static final String CLEAR_USERS = "TRUNCATE TABLE Users";
    private PreparedStatement clearUsersSt;

    private static final String CLEAR_RESERVATIONS = "TRUNCATE TABLE Reservations";
    private PreparedStatement clearReservationsSt;

    private static final String CREATE_USER = "INSERT INTO Users VALUES (?, ?, ?, ?)";
    private PreparedStatement createUSerSt;

    private static final String LOGIN = "SELECT * FROM Users WHERE username = ?";
    private PreparedStatement loginQuery;

    private static final String DIRECT_FLIGHTS = "SELECT TOP (?) "
            + "fid,month_id,day_of_month,carrier_id,flight_num,origin_city,dest_city,actual_time,capacity,price "
            + "FROM Flights " + "WHERE origin_city = ? AND dest_city = ? AND day_of_month = ? " + "AND canceled = 0 "
            + "ORDER BY actual_time ASC, fid ASC";
    private PreparedStatement directFlightQuery;

    private static final String ONEHOP_FLIGHTS = "SELECT TOP (?) F1.fid,F1.day_of_month,F1.carrier_id,F1.flight_num,F1.origin_city,F1.dest_city,F1.actual_time,F1.capacity,F1.price,"
            + "F2.fid,F2.carrier_id,F2.flight_num,F2.origin_city,F2.dest_city,F2.actual_time,F2.capacity,F2.price "
            + "FROM Flights AS F1, Flights AS F2 "
            + "WHERE F1.origin_city = ? AND F1.dest_city = F2.origin_city AND F2.dest_city = ? "
            + "AND F1.day_of_month = F2.day_of_month AND F1.month_id = F2.month_id AND F1.day_of_month = ? "
            + "AND F1.canceled = 0 AND F2.canceled = 0 "
            + "ORDER BY (F1.actual_time + F2.actual_time) ASC, F1.fid ASC, F2.fid ASC";
    private PreparedStatement onehopQuery;

    private static final String SAMEDAY = "SELECT day_of_month FROM Flights, Reservations "
            + "WHERE username = ? AND fid = fid1 AND cancel = 0";
    private PreparedStatement checksamedayQuery;

    private static final String CHECKCAP = "SELECT capacity FROM Flights WHERE fid = ?";
    private PreparedStatement checkCapQuery;
    // For check dangling
    private static final String TRANCOUNT_SQL = "SELECT @@TRANCOUNT AS tran_count";
    private PreparedStatement tranCountStatement;

    private static final String INSERT_RESERVE = "INSERT INTO Reservations VALUES (?, ?, ?, ?, ?, ?)";
    private PreparedStatement insertReserveQuery;

    private static final String GET_RESERVE = "SELECT * FROM Reservations WHERE username = ? AND cancel = 0";
    private PreparedStatement getReserveQuery;

    private static final String GET_FLIGHT = "SELECT * FROM FLIGHTS WHERE fid = ?";
    private PreparedStatement getFlightQuery;

    private static final String GET_UNPAID_R = "SELECT rid FROM Reservations WHERE rid = ? AND paid = 0 AND cancel = 0;\n";
    private PreparedStatement getUnpaidR;

    private static final String PRICE = "SELECT price FROM Flights WHERE fid = ?;\n";
    private PreparedStatement priceQuery;

    private static final String PRICE_R = "SELECT price FROM Reservations WHERE rid = ?;\n";
    private PreparedStatement reservePriceQuery;

    private static final String BALANCE = "SELECT balance FROM Users WHERE username = ?;\n";
    private PreparedStatement balanceQuery;

    private static final String UPDATE_PAID = "UPDATE Reservations SET paid = 1 WHERE rid = ?;\n";
    private PreparedStatement updatePaidQuery;

    private static final String UPDATE_BALANCE = "UPDATE Users SET balance = ? WHERE username = ?;\n";
    private PreparedStatement updateBalanceQuery;

    private static final String GET_R = "SELECT * FROM Reservations WHERE rid = ? AND cancel = 0;\n";
    private PreparedStatement getR;

    private static final String UPDATE_CANCEL = "UPDATE Reservations SET cancel = 1 WHERE rid = ?;\n";
    private PreparedStatement updateCancel;

    private static final String RESERVE_FID1_TAKEN = "SELECT COUNT(*) FROM Reservations WHERE fid1 = ? AND cancel = 0;\n";
    private PreparedStatement reserveFid1taken;

    private static final String RESERVE_FID2_TAKEN = "SELECT COUNT(*) FROM Reservations WHERE fid2 = ? AND cancel = 0;\n";
    private PreparedStatement reserveFid2taken;

    private static final String GET_RID_BOOK = "SELECT MAX(rid) FROM Reservations WHERE username = ?";
    private PreparedStatement rid_book;

    // DEFINE LOCAL VARIABLES AND OBJECTS WE WILL USE.
    private static int loggedin = 0;
    private static String loggedin_user = null;

    public class Itinerary implements Comparable<Itinerary> {
        int iid;
        int fid1;
        int fid2;
        int price1;
        int price2;
        int day1;
        int day2;
        int time1;
        int time2;
        String carrierId1;
        String carrierId2;
        String origincity1;
        String origincity2;
        String destcity1;
        String destcity2;
        String flightnum1;
        String flightnum2;
        int capacity1;
        int capacity2;

        public Itinerary(int iid, int fid1, int fid2, int price1, int price2, int day1, int day2, int time1, int time2, String carrierID1, String carrierID2, String origincity1, String origincity2, String destcity1, String destcity2, String flightnum1, String flightnum2, int capacity1, int capacity2) {
            this.iid = iid;
            this.fid1 = fid1;
            this.fid2 = fid2;
            this.price1 = price1;
            this.price2 = price2;
            this.day1 = day1;
            this.day2 = day2;
            this.time1 = time1;
            this.time2 = time2;
            this.carrierId1 = carrierID1;
            this.carrierId2 = carrierID2;
            this.origincity1 = origincity1;
            this.origincity2 = origincity2;
            this.destcity1 = destcity1;
            this.destcity2 = destcity2;
            this.flightnum1 = flightnum1;
            this.flightnum2 = flightnum2;
            this.capacity1 = capacity1;
            this.capacity2 = capacity2;

        }

        @Override
        public String toString() {
            return "Iid: " + this.iid + " fid1: " + this.fid1 + " fid2: " + this.fid2 + " price: " + this.price
                    + " day_of_month: " + this.day;
        }

        public int compareTo(Itinerary compareItinerary) {
            if ((this.time1 + this.time2) == (compareItinerary.time1 + compareItinerary.time2) && (this.fid1 == compareItinerary.fid1)) {
                return this.fid2 - compareItinerary.fid2;
            } else if ((this.time1 + this.time2) == (compareItinerary.time1 + compareItinerary.time2)) {
                return this.fid1 - compareItinerary.fid2;
            } else {
                return (this.time1 + this.time2) - (compareItinerary.time1 + compareItinerary.time2);
            }
        }
    }

    class Flight {
        public int fid;
        public int dayOfMonth;
        public String carrierId;
        public String flightNum;
        public String originCity;
        public String destCity;
        public int time;
        public int capacity;
        public int price;

        @Override
        public String toString() {
            return "ID: " + fid + " Day: " + dayOfMonth + " Carrier: " + carrierId + " Number: " + flightNum
                    + " Origin: " + originCity + " Dest: " + destCity + " Duration: " + time + " Capacity: " + capacity
                    + " Price: " + price;
        }
    }

    private Itinerary itineraryArray[];

    private Itinerary[] appendValue(Itinerary[] obj, Itinerary newObj) {
        ArrayList<Itinerary> temp = new ArrayList<Itinerary>(Arrays.asList(obj));
        temp.add(newObj);
        return temp.toArray(new Itinerary[temp.size()]);
    }

    /**
     * Throw IllegalStateException if transaction not completely complete, rollback.
     */
    private void checkDanglingTransaction() {
        try {
            try (ResultSet rs = tranCountStatement.executeQuery()) {
                rs.next();
                int count = rs.getInt("tran_count");
                if (count > 0) {
                    throw new IllegalStateException(
                            "Transaction not fully commit/rollback. Number of transaction in process: " + count);
                }
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Database error", e);
        }
    }

    private static boolean isDeadLock(SQLException ex) {
        return ex.getErrorCode() == 1205;
    }

    public Query() throws SQLException, IOException {
        this(null, null, null, null);
    }

    protected Query(String serverURL, String dbName, String adminName, String password)
            throws SQLException, IOException {
        conn = serverURL == null ? openConnectionFromDbConn()
                : openConnectionFromCredential(serverURL, dbName, adminName, password);

        prepareStatements();
    }

    /**
     * Return a connecion by using dbconn.properties file
     *
     * @throws SQLException
     * @throws IOException
     */
    public static Connection openConnectionFromDbConn() throws SQLException, IOException {
        // Connect to the database with the provided connection configuration
        Properties configProps = new Properties();
        configProps.load(new FileInputStream("dbconn.properties"));
        String serverURL = configProps.getProperty("hw5.server_url");
        String dbName = configProps.getProperty("hw5.database_name");
        String adminName = configProps.getProperty("hw5.username");
        String password = configProps.getProperty("hw5.password");
        return openConnectionFromCredential(serverURL, dbName, adminName, password);
    }

    /**
     * Return a connecion by using the provided parameter.
     *
     * @param serverURL example: example.database.widows.net
     * @param dbName    database name
     * @param adminName username to login server
     * @param password  password to login server
     * @throws SQLException
     */
    protected static Connection openConnectionFromCredential(String serverURL, String dbName, String adminName,
            String password) throws SQLException {
        String connectionUrl = String.format("jdbc:sqlserver://%s:1433;databaseName=%s;user=%s;password=%s", serverURL,
                dbName, adminName, password);
        Connection conn = DriverManager.getConnection(connectionUrl);

        // By default, automatically commit after each statement
        conn.setAutoCommit(true);

        // By default, set the transaction isolation level to serializable
        conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);

        return conn;
    }

    /**
     * Get underlying connection
     */
    public Connection getConnection() {
        return conn;
    }

    /**
     * Closes the application-to-database connection
     */
    public void closeConnection() throws SQLException {
        conn.close();
    }

    /**
     * Clear the data in any custom tables created.
     * <p>
     * WARNING! Do not drop any tables and do not clear the flights table.
     */
    public void clearTables() {
        try {
            // CLEAR USERS
            clearUsersSt.clearParameters();
            clearUsersSt.executeUpdate();

            // CLEAR RESERVATIONS
            clearReservationsSt.clearParameters();
            clearReservationsSt.executeUpdate();

            // RESETS LOCAL VARIABLES.
            loggedin = 0;
            loggedin_user = null;

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*
     * prepare all the SQL statements in this method.
     */
    private void prepareStatements() throws SQLException {
        tranCountStatement = conn.prepareStatement(TRANCOUNT_SQL);
        clearUsersSt = conn.prepareStatement(CLEAR_USERS);
        clearReservationsSt = conn.prepareStatement(CLEAR_RESERVATIONS);
        createUSerSt = conn.prepareStatement(CREATE_USER);
        loginQuery = conn.prepareStatement(LOGIN);
        directFlightQuery = conn.prepareStatement(DIRECT_FLIGHTS);
        onehopQuery = conn.prepareStatement(ONEHOP_FLIGHTS);
        checksamedayQuery = conn.prepareStatement(SAMEDAY);
        checkCapQuery = conn.prepareStatement(CHECKCAP);
        insertReserveQuery = conn.prepareStatement(INSERT_RESERVE);
        getReserveQuery = conn.prepareStatement(GET_RESERVE);
        getFlightQuery = conn.prepareStatement(GET_FLIGHT);
        getUnpaidR = conn.prepareStatement(GET_UNPAID_R);
        priceQuery = conn.prepareStatement(PRICE);
        balanceQuery = conn.prepareStatement(BALANCE);
        reservePriceQuery = conn.prepareStatement(PRICE_R);
        updatePaidQuery = conn.prepareStatement(UPDATE_PAID);
        updateBalanceQuery = conn.prepareStatement(UPDATE_BALANCE);
        getR = conn.prepareStatement(GET_R);
        updateCancel = conn.prepareStatement(UPDATE_CANCEL);
        generalStatement = conn.createStatement();
        reserveFid1taken = conn.prepareStatement(RESERVE_FID1_TAKEN);
        reserveFid2taken = conn.prepareStatement(RESERVE_FID2_TAKEN);
        rid_book = conn.prepareStatement(GET_RID_BOOK);

    }

    /**
     * Takes a user's username and password and attempts to log the user in.
     *
     * @param username user's username
     * @param password user's password
     * @return If someone has already logged in, then return "User already logged
     *         in\n" For all other errors, return "Login failed\n". Otherwise,
     *         return "Logged in as [username]\n".
     * @throws SQLException
     */
    public String transaction_login(String username, String password) {
        try {
            // ALREADY LOGGED IN? LOGIN FAILS.
            if (loggedin == 1) {
                return "User already logged in\n";
            }

            // RETRIEVE THE USER INFO.
            String username_to_check = null;
            byte[] hashed_password_retrieved = new byte[16];
            byte[] salt_retrieved = new byte[16];
            loginQuery.clearParameters();
            loginQuery.setString(1, username);
            ResultSet rs = loginQuery.executeQuery();
            while (rs.next()) {
                username_to_check = rs.getString(1);
                hashed_password_retrieved = rs.getBytes(2);
                salt_retrieved = rs.getBytes(3);
            }

            // USERNAME DOES NOT EXIST? LOGIN FAILS.
            if (username_to_check == null) {
                return "Login failed\n";
            }

            // FIND HASHED PASSWORD.
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt_retrieved, HASH_STRENGTH, KEY_LENGTH);
            SecretKeyFactory factory = null;
            byte[] hash_to_check = null;
            try {
                factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
                hash_to_check = factory.generateSecret(spec).getEncoded();
            } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
                throw new IllegalStateException();
            }

            // CHECK THE HASHED PASSWORD IS SAME AS THE DB.
            if (Arrays.equals(hash_to_check, hashed_password_retrieved)) {
                loggedin = 1;
                loggedin_user = username;
                return "Logged in as " + username + "\n";
            } else {
                return "Login failed\n";
            }
        } catch (SQLException e) {
            return "Login failed\n";
        } finally {
            checkDanglingTransaction();
        }
    }

    /**
     * Implement the create user function.
     *
     * @param username   new user's username. User names are unique the system.
     * @param password   new user's password.
     * @param initAmount initial amount to deposit into the user's account, should
     *                   be >= 0 (failure otherwise).
     * @return either "Created user {@code username}\n" or "Failed to create user\n"
     *         if failed.
     */
    public String transaction_createCustomer(String username, String password, int initAmount) {
        try {
            // BALANCE NEGATIVE? CREATE FAILS.
            if (initAmount < 0) {
                return "Failed to create user\n";
            }

            // GET SALT AND CALCULATE HASH.
            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[16];
            random.nextBytes(salt);
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, HASH_STRENGTH, KEY_LENGTH);
            SecretKeyFactory factory = null;
            byte[] hash = null;
            try {
                factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
                hash = factory.generateSecret(spec).getEncoded();
            } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
                throw new IllegalStateException();
            }

            // INSERT INTO DB.
            createUSerSt.clearParameters();
            createUSerSt.setString(1, username);
            createUSerSt.setBytes(2, hash);
            createUSerSt.setBytes(3, salt);
            createUSerSt.setInt(4, initAmount);
            createUSerSt.execute();

            return "Created user " + username + "\n";
        } catch (SQLException e) {
            return "Failed to create user\n";
        } finally {
            checkDanglingTransaction();
        }
    }

    /**
     * Implement the search function.
     * <p>
     * Searches for flights from the given origin city to the given destination
     * city, on the given day of the month. If {@code directFlight} is true, it only
     * searches for direct flights, otherwise is searches for direct flights and
     * flights with two "hops." Only searches for up to the number of itineraries
     * given by {@code numberOfItineraries}.
     * <p>
     * The results are sorted based on total flight time.
     *
     * @param originCity
     * @param destinationCity
     * @param directFlight        if true, then only search for direct flights,
     *                            otherwise include indirect flights as well
     * @param dayOfMonth
     * @param numberOfItineraries number of itineraries to return
     * @return If no itineraries were found, return "No flights match your
     *         selection\n". If an error occurs, then return "Failed to search\n".
     *         <p>
     *         Otherwise, the sorted itineraries printed in the following format:
     *         <p>
     *         Itinerary [itinerary number]: [number of flights] flight(s), [total
     *         flight time] minutes\n [first flight in itinerary]\n ... [last flight
     *         in itinerary]\n
     *         <p>
     *         Each flight should be printed using the same format as in the
     *         {@code Flight} class. Itinerary numbers in each search should always
     *         start from 0 and increase by 1. // * @see Flight#toString()
     */
    public String transaction_search(String originCity, String destinationCity, boolean directFlight, int dayOfMonth,
            int numberOfItineraries) {
        try {
            StringBuffer sb = new StringBuffer();
            itineraryArray = new Itinerary[0]; // INITIALIZE THE ITINERARYARRAY WHENEVER NEW SEARCH IS REQUESTED.
            int count = 0;

            // RETRIEVE THE FLIGHT INFORMATION.
            directFlightQuery.clearParameters();
            directFlightQuery.setInt(1, numberOfItineraries);
            directFlightQuery.setString(2, originCity);
            directFlightQuery.setString(3, destinationCity);
            directFlightQuery.setInt(4, dayOfMonth);
            ResultSet directResult = directFlightQuery.executeQuery();

            while (directResult.next()) {
                int result_fid = directResult.getInt("fid");
                int result_dayOfMonth = directResult.getInt("day_of_month");
                String result_carrierId = directResult.getString("carrier_id");
                String result_flightNum = directResult.getString("flight_num");
                String result_originCity = directResult.getString("origin_city");
                String result_destCity = directResult.getString("dest_city");
                int result_time = directResult.getInt("actual_time");
                int result_capacity = directResult.getInt("capacity");
                int result_price = directResult.getInt("price");

                // PUT INTO THE INTINERARYARRAY THE RETRIEVED INFORMATION.
                itineraryArray = appendValue(itineraryArray, 
                    new Itinerary(0, result_fid, -1, result_price, -1, result_dayOfMonth, -1, result_time, -1, 
                                    result_carrierId, null, result_originCity, null, result_destCity, null, result_flightNum, null, result_capacity, -1));
                count++;
            }
            directResult.close();

            // RETRIEVE THE ONESTOP FLIGHT INFORMATION.
            if (count < numberOfItineraries & (!directFlight)) {
                int totalIndf = numberOfItineraries - count;
                onehopQuery.clearParameters();
                onehopQuery.setInt(1, totalIndf);
                onehopQuery.setString(2, originCity);
                onehopQuery.setString(3, destinationCity);
                onehopQuery.setInt(4, dayOfMonth);
                ResultSet oneHopResults = onehopQuery.executeQuery();
                while (oneHopResults.next()) {
                    int r_fid1 = oneHopResults.getInt(1);
                    int r_fid2 = oneHopResults.getInt(10);
                    int r_dayOfMonth = oneHopResults.getInt(2);
                    String r_carrier1 = oneHopResults.getString(3);
                    String r_carrier2 = oneHopResults.getString(11);
                    String r_flightNum1 = oneHopResults.getString(4);
                    String r_flightNum2 = oneHopResults.getString(12);
                    String r_org1 = oneHopResults.getString(5);
                    String r_org2 = oneHopResults.getString(13);
                    String r_dest1 = oneHopResults.getString(6);
                    String r_dest2 = oneHopResults.getString(14);
                    int r_time1 = oneHopResults.getInt(7);
                    int r_time2 = oneHopResults.getInt(15);
                    int r_cap1 = oneHopResults.getInt(8);
                    int r_cap2 = oneHopResults.getInt(16);
                    int r_price1 = oneHopResults.getInt(9);
                    int r_price2 = oneHopResults.getInt(17);

                    itineraryArray = appendValue(itineraryArray,
                            new Itinerary(0, r_fid1, r_fid2, r_price1, r_price2, r_dayOfMonth, r_dayOfMonth, r_time1, r_time2, 
                                        r_carrier1, r_carrier2, r_org1, r_org2, r_dest1, r_dest2, r_flightNum1, r_flightNum2, r_cap1, r_cap2));
                    count++;
                }
                oneHopResults.close();
            }

            // SORT THE ARRAY IN ASCENDING FLIGHT TIME.
            Arrays.sort(itineraryArray);
            int count2 = 0;
            // PRINT THE INFORMATIONS
            for (Itinerary infos : itineraryArray) {
                if (infos.fid2 == -1) {
                    infos.iid = count2;
                    sb.append("Itinerary " + infos.iid + ": 1 flight(s), " + infos.time1 + " minutes\n" + "ID: " + infos.fid1
                                + " Day: " + infos.day1 + " Carrier: " + infos.carrierId1 + " Number: "
                                + infos.flightnum1 + " Origin: " + infos.origincity1 + " Dest: " + infos.destcity1
                                + " Duration: " + infos.time1 + " Capacity: " + infos.capacity1 + " Price: " + infos.price1
                                + "\n");
                    count2 ++;
                } else {
                    infos.iid = count2;
                    sb.append("Itinerary " + infos.iid + ": 2 flight(s), " + (infos.time1 + infos.time2) + " minutes\n" + "ID: "
                                + infos.fid1 + " Day: " + infos.day1 + " Carrier: " + infos.carrierId1 + " Number: " + infos.flightnum1
                                + " Origin: " + infos.origincity1 + " Dest: " + infos.destcity1 + " Duration: " + infos.time1 + " Capacity: "
                                + infos.capacity1 + " Price: " + infos.price1 + "\n" + "ID: " + infos.fid2 + " Day: " + infos.day2
                                + " Carrier: " + infos.carrierId2 + " Number: " + infos.flightnum2 + " Origin: " + infos.origincity2 + " Dest: "
                                + infos.destcity2 + " Duration: " + infos.time2 + " Capacity: " + infos.capacity2 + " Price: " + infos.price2
                                + "\n");
                    count2 ++;
                }
            }

            // IF NO FLIGHTS MATCHES THE FILTER, RETURN NO FLIGHTS.
            if (itineraryArray.length == 0) {
                sb.append("No flights match your selection\n");
            }
            return sb.toString();
        } catch (SQLException e) {
            return "Failed to search\n";
        } finally {
            checkDanglingTransaction();
        }
    }

    /**
     * Implements the book itinerary function.
     *
     * @param itineraryId ID of the itinerary to book. This must be one that is
     *                    returned by search in the current session.
     * @return If the user is not logged in, then return "Cannot book reservations,
     *         not logged in\n". If the user is trying to book an itinerary with an
     *         invalid ID or without having done a search, then return "No such
     *         itinerary {@code itineraryId}\n". If the user already has a
     *         reservation on the same day as the one that they are trying to book
     *         now, then return "You cannot book two flights in the same day\n". For
     *         all other errors, return "Booking failed\n".
     *         <p>
     *         And if booking succeeded, return "Booked flight(s), reservation ID:
     *         [reservationId]\n" where reservationId is a unique number in the
     *         reservation system that starts from 1 and increments by 1 each time a
     *         successful reservation is made by any user in the system.
     * @throws SQLException
     */

    public String transaction_book(int itineraryId) {
        // NOT LOGGED IN? BOOK FAILS.
        if (loggedin_user == null) {
            return "Cannot book reservations, not logged in\n";
        }

        // NO SEARCH? INVALID ID? BOOK FAILS.
        if (itineraryArray == null || itineraryId < 0) {
            return "No such itinerary " + itineraryId + "\n";
        }

        // INVALID ID? BOOK FAILS.
        if (itineraryArray.length <= itineraryId) {
            return "No such itinerary " + itineraryId + "\n";
        }

        // CONCURRENCY PROBLEMS CAN HAPPEN FROM HERE. TRANSACTION CONTROL BEGINS.
        try {
            generalStatement.execute(BEGIN_TRANSACTION_SQL);

            Itinerary booked = itineraryArray[itineraryId];

            // CHECK SAME DAY BOOKING.
            checksamedayQuery.clearParameters();
            checksamedayQuery.setString(1, loggedin_user);
            ResultSet bookRS = checksamedayQuery.executeQuery();
            while (bookRS.next()) {
                int r_day = bookRS.getInt(1);
                if (booked.day1 == r_day) {
                    generalStatement.executeUpdate(ROLLBACK_SQL);
                    bookRS.close();
                    return "You cannot book two flights in the same day\n";
                }
            }

            // CHECK FLIGHT CAPACITY.
            int max_seats;
            int taken_seats = 0;

            checkCapQuery.clearParameters();
            checkCapQuery.setInt(1, booked.fid1);
            ResultSet checkCapRS = checkCapQuery.executeQuery();
            checkCapRS.next();
            max_seats = checkCapRS.getInt(1);
            checkCapRS.close();

            reserveFid1taken.clearParameters();
            reserveFid1taken.setInt(1, booked.fid1);
            checkCapRS = reserveFid1taken.executeQuery();
            checkCapRS.next();
            taken_seats = checkCapRS.getInt(1);
            checkCapRS.close();

            if (taken_seats >= max_seats) {
                generalStatement.executeUpdate(ROLLBACK_SQL); // IF FULLY BOOKED, BOOK FAILS. END TRANSACTION.
                return "Booking failed\n";
            }

            // CHECK CAPACITY OF THE SECOND FLIGHT.
            int max_seats2;
            int taken_seats2 = 0;

            if (booked.fid2 != -1) {
                checkCapQuery.clearParameters();
                checkCapQuery.setInt(1, booked.fid2);
                checkCapRS = checkCapQuery.executeQuery();
                checkCapRS.next();
                max_seats2 = checkCapRS.getInt(1);
                checkCapRS.close();

                reserveFid2taken.clearParameters();
                reserveFid2taken.setInt(1, booked.fid2);
                checkCapRS = reserveFid2taken.executeQuery();
                checkCapRS.next();
                taken_seats2 = checkCapRS.getInt(1);
                checkCapRS.close();

                if (taken_seats2 >= max_seats2) {
                    generalStatement.executeUpdate(ROLLBACK_SQL);
                    return "Booking failed\n";
                }
            }

            // INSERT INTO RESERVATION.
            int total_price;
            insertReserveQuery.clearParameters();
            insertReserveQuery.setInt(1, 0); // 0 MEANS UNPAID.
            insertReserveQuery.setInt(2, 0); // 0 MEANS UNCANCELED.
            insertReserveQuery.setInt(3, booked.fid1);

            // GET PRICE.
            priceQuery.clearParameters();
            priceQuery.setInt(1, booked.fid1);
            ResultSet f1price = priceQuery.executeQuery();
            f1price.next();
            total_price = f1price.getInt(1);
            f1price.close();
            if (booked.fid2 == -1) {
                insertReserveQuery.setInt(4, -1);
            } else {
                insertReserveQuery.setInt(4, booked.fid2);
                priceQuery.clearParameters();
                priceQuery.setInt(1, booked.fid2);
                ResultSet f2price = priceQuery.executeQuery();
                f2price.next();
                total_price += f2price.getInt(1);
                f2price.close();
            }
            insertReserveQuery.setString(5, loggedin_user);
            insertReserveQuery.setInt(6, total_price);
            insertReserveQuery.execute();

            // GET RESERVATION ID.
            rid_book.setString(1, loggedin_user);
            ResultSet getRid = rid_book.executeQuery();
            getRid.next();
            int rid = getRid.getInt(1);
            getRid.close();

            // BOOK SUCCESSFUL. END TRANSACTION.
            generalStatement.execute(COMMIT_SQL);
            return "Booked flight(s), reservation ID: " + rid + "\n";

        } catch (SQLException e) {
            // ANY OTHER ERRORS RETURN FAILURE.
            try {
                generalStatement.executeUpdate(ROLLBACK_SQL);
            } catch (SQLException e1) {
                return "Booking failed\n";
            }
            return "Booking failed\n";
        } finally {
            checkDanglingTransaction();
        }
    }


    /**
     * Implements the pay function.
     *
     * @param reservationId the reservation to pay for.
     * @return If no user has logged in, then return "Cannot pay, not logged in\n" If the reservation
     * is not found / not under the logged in user's name, then return "Cannot find unpaid
     * reservation [reservationId] under user: [username]\n" If the user does not have enough
     * money in their account, then return "User has only [balance] in account but itinerary
     * costs [cost]\n" For all other errors, return "Failed to pay for reservation
     * [reservationId]\n"
     * <p>
     * If successful, return "Paid reservation: [reservationId] remaining balance:
     * [balance]\n" where [balance] is the remaining balance in the user's account.
     */
    public String transaction_pay(int reservationId) {
        // NOT LOGGED IN? PAY FAILS.
        if (loggedin_user == null) {
            return "Cannot pay, not logged in\n";
        }
        try {
            // TRANSACTION BEGINS HERE FOR WE DON'T WANT CONCURRENCY PROBLEM.
            generalStatement.execute(BEGIN_TRANSACTION_SQL);
            
            // RETRIEVED THE PAID INFORMATION OF THE CHOSEN RESERVATION.
            getUnpaidR.clearParameters();
            getUnpaidR.setInt(1, reservationId); // 
            ResultSet getRS = getUnpaidR.executeQuery();

            // CHECK THE GIVEN ID. IF NO SUCH ID NOT FOUND OR ALREADY PAID, PAY FAILS.
            if (getRS.next() == false) {
                getRS.close();
                generalStatement.execute(ROLLBACK_SQL);
                return "Cannot find unpaid reservation " + reservationId + " under user: " + loggedin_user + "\n";
            } else {
                getRS.close();
            }

            // GET THE PRICE INFORMATION FROM THE RESERVATION.
            reservePriceQuery.clearParameters();
            reservePriceQuery.setInt(1, reservationId);
            ResultSet getRSPrice = reservePriceQuery.executeQuery();
            getRSPrice.next();
            int need = getRSPrice.getInt(1);
            getRSPrice.close();

            // GET THE USER BALANCE.
            balanceQuery.clearParameters();
            balanceQuery.setString(1, loggedin_user);
            ResultSet getBalance = balanceQuery.executeQuery();
            getBalance.next();
            int balance = getBalance.getInt(1);
            getBalance.close();

            // IF BALANCE IS NOT ENOUGH, PAY FAILS. 
            if (balance < need) {
                generalStatement.execute(ROLLBACK_SQL);
                return "User has only " + balance + " in account but itinerary costs " + need + "\n";
            }

            // UPDATE THE RESERVATION TO CONFIRM PAYMENT.
            updatePaidQuery.clearParameters();
            updatePaidQuery.setInt(1, reservationId);
            updatePaidQuery.execute();

            // DEDUCT FROM USER BALANCE. 
            int remaining_balance = balance - need;
            updateBalanceQuery.clearParameters();
            updateBalanceQuery.setInt(1, remaining_balance);
            updateBalanceQuery.setString(2, loggedin_user);
            updateBalanceQuery.execute();
            generalStatement.execute(COMMIT_SQL);
            return "Paid reservation: " + reservationId + " remaining balance: " + remaining_balance + "\n";
        } catch (SQLException e) {
            return "Failed to pay for reservation " + reservationId + "\n";
        } finally {
            checkDanglingTransaction();
        }
    }

    /**
     * Implements the reservations function.
     *
     * @return If no user has logged in, then return "Cannot view reservations, not
     *         logged in\n" If the user has no reservations, then return "No
     *         reservations found\n" For all other errors, return "Failed to
     *         retrieve reservations\n"
     *         <p>
     *         Otherwise return the reservations in the following format:
     *         <p>
     *         Reservation [reservation ID] paid: [true or false]:\n [flight 1 under
     *         the reservation]\n [flight 2 under the reservation]\n Reservation
     *         [reservation ID] paid: [true or false]:\n [flight 1 under the
     *         reservation]\n [flight 2 under the reservation]\n ...
     *         <p>
     *         Each flight should be printed using the same format as in the
     *         {@code Flight} class.
     * @throws SQLException
     * @see Flight#toString()
     */
    public String transaction_reservations() {
        // IF NOT LOGGED IN, VIEWING FAILS.
        if (loggedin_user == null) {
            return "Cannot view reservations, not logged in\n";
        }

        try {
            // TRANSACTION BEGINS.
            generalStatement.execute(BEGIN_TRANSACTION_SQL);

            boolean emptyReserve = true; // VARIABLE TO CHECK WHETHER EMPTY.
            StringBuffer sb = new StringBuffer();

            getReserveQuery.clearParameters();
            getReserveQuery.setString(1, loggedin_user);
            ResultSet getReserveRS = getReserveQuery.executeQuery();
    
            while (getReserveRS.next()) {
                // GET RESERVATION INFO.
                emptyReserve = false;
                int r_rid = getReserveRS.getInt("rid");
                int r_paid = getReserveRS.getInt("paid");
                int r_fid1 = getReserveRS.getInt("fid1");
                int r_fid2 = getReserveRS.getInt("fid2");
                String paid = (r_paid == 1) ? "true" : "false";
                sb.append("Reservation ").append(r_rid).append(" paid: ").append(paid).append(":\n");
                
                // GET FLIGHT INFO.
                getFlightQuery.clearParameters();
                getFlightQuery.setInt(1, r_fid1);
                ResultSet getfinfo = getFlightQuery.executeQuery();
                getfinfo.next();
                int rf_fid = getfinfo.getInt("fid");
                int rf_day_of_month = getfinfo.getInt("day_of_month");
                String rf_cid = getfinfo.getString("carrier_id");
                int rf_flight_num = getfinfo.getInt("flight_num");
                String rf_org_city = getfinfo.getString("origin_city");
                String rf_dest_city = getfinfo.getString("dest_city");
                int rf_actual_time = getfinfo.getInt("actual_time");
                int rf_capacity = getfinfo.getInt("capacity");
                int rf_price = getfinfo.getInt("price");
                getfinfo.close();
                sb.append("ID: ").append(rf_fid).append(" Day: ").append(rf_day_of_month)
                        .append(" Carrier: ").append(rf_cid).append(" Number: ").append(rf_flight_num)
                        .append(" Origin: ").append(rf_org_city).append(" Dest: ").append(rf_dest_city)
                        .append(" Duration: ").append(rf_actual_time).append(" Capacity: ").append(rf_capacity)
                        .append(" Price: ").append(rf_price).append("\n");
                
                // GET 2ND FLIGHT INFO.
                if (r_fid2 != -1) {
                    getFlightQuery.clearParameters();
                    getFlightQuery.setInt(1, r_fid2);
                    getfinfo = getFlightQuery.executeQuery();
                    getfinfo.next();
                    rf_fid = getfinfo.getInt("fid");
                    rf_day_of_month = getfinfo.getInt("day_of_month");
                    rf_cid = getfinfo.getString("carrier_id");
                    rf_flight_num = getfinfo.getInt("flight_num");
                    rf_org_city = getfinfo.getString("origin_city");
                    rf_dest_city = getfinfo.getString("dest_city");
                    rf_actual_time = getfinfo.getInt("actual_time");
                    rf_capacity = getfinfo.getInt("capacity");
                    rf_price = getfinfo.getInt("price");
                    getfinfo.close();
                    sb.append("ID: ").append(rf_fid).append(" Day: ").append(rf_day_of_month)
                            .append(" Carrier: ").append(rf_cid).append(" Number: ").append(rf_flight_num)
                            .append(" Origin: ").append(rf_org_city).append(" Dest: ").append(rf_dest_city)
                            .append("Duration: ").append(rf_actual_time).append(" Capacity: ").append(rf_capacity)
                            .append(" Price: ").append(rf_price).append("\n");
                }
            }
            getReserveRS.close();

            // IF EMPTY, NO RESERVATIONS!
            if (emptyReserve) {
                generalStatement.execute(ROLLBACK_SQL);
                return "No reservations found\n";
            } else {
                generalStatement.execute(COMMIT_SQL);
                return sb.toString();
            }
        } catch (SQLException e) {
            try {
                generalStatement.executeUpdate(ROLLBACK_SQL);
            } catch (SQLException e1) {
                return "Failed to retrieve reservations\n";
            }
            return "Failed to retrieve reservations\n";
        } finally {
            checkDanglingTransaction();
        }
    }

    /**
     * Implements the cancel operation.
     *
     * @param reservationId the reservation ID to cancel
     * @return If no user has logged in, then return "Cannot cancel reservations,
     *         not logged in\n" For all other errors, return "Failed to cancel
     *         reservation [reservationId]\n"
     *         <p>
     *         If successful, return "Canceled reservation [reservationId]\n"
     *         <p>
     *         Even though a reservation has been canceled, its ID should not be
     *         reused by the system.
     * @throws SQLException
     */
    public String transaction_cancel(int reservationId) {
        // IF NOT LOGGED IN, CANCEL FAILS.
        if (loggedin_user == null)
        {
            return "Cannot cancel reservations, not logged in\n";
        }
        try {
            // TRANSACTION BEGINS.
            generalStatement.execute(BEGIN_TRANSACTION_SQL);
            getR.clearParameters();
            getR.setInt(1, reservationId);
            ResultSet cancelRS = getR.executeQuery();

            // NO SUCH RESERVATION? CANCEL FAILS.
            if(cancelRS.next() == false) {
                cancelRS.close();
                generalStatement.execute(ROLLBACK_SQL);
                return "Failed to cancel reservation " + reservationId + "\n";
            }

            // IF PAID, RECORD HOW MUCH TO REFUND.
            int refund = (cancelRS.getInt("paid") == 1) ? cancelRS.getInt("price") : 0;
            cancelRS.close();

            // ADD REFUND AMOUNT TO BALANCE.
            balanceQuery.setString(1,loggedin_user);
            ResultSet balanceRS = balanceQuery.executeQuery();
            balanceRS.next();
            int balance = balanceRS.getInt("balance");
            balanceRS.close();

            updateBalanceQuery.clearParameters();
            updateBalanceQuery.setInt(1, balance + refund);
            updateBalanceQuery.setString(2, loggedin_user);
            updateBalanceQuery.execute();

            // FINALLY UPDATE THE CANCEL COLUMN IN RESERVATION.
            updateCancel.clearParameters();
            updateCancel.setInt(1, reservationId);
            updateCancel.execute();

            generalStatement.execute(COMMIT_SQL);
            return "Canceled reservation " + reservationId + "\n";
        } catch (SQLException e) {
            try {
                generalStatement.executeUpdate(ROLLBACK_SQL);
            } catch (SQLException e1) {
                return "Booking failed\n";
            }
            return "Failed to cancel reservation " + reservationId + "\n";
        } finally {
            checkDanglingTransaction();
        }
    }
}


