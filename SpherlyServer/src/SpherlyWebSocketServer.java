import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.bluetooth.RemoteDevice;
import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.Vector;

public class SpherlyWebSocketServer extends WebSocketServer {
    private Sphero sp;
    private Vector<RemoteDevice> sp_devices;
    private Object lock;
    private Main commandPrompt;

    private Thread listDevicesThread;
    private Thread connectionThread;

    public SpherlyWebSocketServer(Main commandPrompt) throws UnknownHostException {
        super(new InetSocketAddress(8080));
        this.commandPrompt = commandPrompt;
        displayMessage("Server is up!");

        lock = new Object();
        sp = new Sphero(this);
        sp_devices = null;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        displayMessage("Connected to webclient!");
        sp = new Sphero(this);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        displayMessage("Disconnected from webclient");
        sp.disconnect();
        sp = null;
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        JSONObject data;
        try {
            data = (JSONObject) new JSONParser().parse(message);
        } catch (ParseException e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            displayError(sw.toString());
            return;
        }

        //Respond to the command
        String command = (String) data.get("command");
        //System.out.println("Command: " + command);
        if (command.equals("listDevices")) {
            class ListDevicesThread extends Thread {
                JSONObject data;
                WebSocket conn;

                public ListDevicesThread(JSONObject data, WebSocket conn) {
                    this.data = data;
                    this.conn = conn;
                }

                public void run() {
                    try {
                        JSONArray response = listDevices(data);
                        conn.send(response.toJSONString());
                    } catch (NullPointerException e) {

                    }
                }
            }
            listDevicesThread = new ListDevicesThread(data, conn);
            listDevicesThread.start();
        } else if (command.equals("cancelListDevices")) {
            displayMessage("cancel device search");
            listDevicesThread.interrupt();
            listDevicesThread = null;
        } else if (command.equals("connectToDevice")) {
            class ConnectionThread extends Thread {
                JSONObject data;
                WebSocket conn;

                public ConnectionThread(JSONObject data, WebSocket conn) {
                    this.data = data;
                    this.conn = conn;
                }

                public void run() {
                    try {
                        JSONObject response = connectToDevice(data);
                        conn.send(response.toJSONString());
                    } catch (NullPointerException e) {

                    }

                    final WebSocket connection = conn;
                    Sphero.SpheroHandler onPowerNotification = new Sphero.SpheroHandler() {
                        public void handle(byte data) {
                            JSONObject response = new JSONObject();
                            response.put("power", true);
                            response.put("data", data);
                            if (data == 1) {
                                displayMessage("battery charging");
                            } else if (data == 3) {
                                displayMessage("battery low");
                            } else if (data == 4) {
                                displayMessage("battery critical!");
                            }

                            connection.send(response.toJSONString());
                        }

                        public void handle() {
                        }
                    };
                    sp.setPowerNotificationHandler(onPowerNotification);
                    sp.enablePowerNotification(true);
                }
            }
            connectionThread = new ConnectionThread(data, conn);
            connectionThread.start();
        } else if (command.equals("cancelConnection")) {
            displayMessage("cancel attempted connection");
            connectionThread.interrupt();
            connectionThread = null;
        } else if (command.equals("disconnect")) {
            try {
                sp.disconnect();
            } catch (Exception e) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                displayError(sw.toString());
            }
        } else if (command.equals("setRGB")) {
            displayMessage("Set RGB, R: " + data.get("red") +
                    ", G: " + data.get("green") +
                    ", B:" + data.get("blue"));
            int red = ((Long) data.get("red")).intValue();
            int green = ((Long) data.get("green")).intValue();
            int blue = ((Long) data.get("blue")).intValue();
            sp.setRGB(red, green, blue);
        } else if (command.equals("roll")) {
            int speed = ((Long) data.get("speed")).intValue();
            int heading = ((Long) data.get("heading")).intValue();
            if (speed < 0) speed = 0;
            if (speed > 255) speed = 255;
            displayMessage("roll: heading=" + heading + ", speed=" + speed);
            sp.roll(heading, speed);
        } else if (command.equals("rollForward")) {
            int speed = ((Long) data.get("speed")).intValue();
            if (speed < 0) speed = 0;
            if (speed > 255) speed = 255;
            displayMessage("roll Forward: speed=" + speed);
            sp.rollForward(speed);
        } else if (command.equals("turn")) {
            int direction = ((Long) data.get("direction")).intValue();
            displayMessage("turn: direction=" + direction);
            sp.turn(direction);
        }else if (command.equals("setHeading")){
        	int heading = ((Long) data.get("heading")).intValue();
        	displayMessage("setHeading: heading=" + heading);
        	sp.setHeading(heading);
        }else if (command.equals("resetHeading")){
        	displayMessage("resetHeading");
        	sp.resetHeading();
        } else if (command.equals("setStabilization")) {
            boolean flag = (Boolean) data.get("flag");
            displayMessage("Set Stabilization: " + flag);
            sp.setStabilization(flag);
        } else if (command.equals("setRotationRate")) {
            int rate = ((Long) data.get("rate")).intValue();
            displayMessage("Set Rotation Rate: " + rate);
            sp.setRotationRate(rate);
        } else if (command.equals("stop")) {
            displayMessage("stop Rolling");
            sp.stop();
        } else if (command.equals("calibrateOn")){
            displayMessage("Calibrate ON");
            sp.calibrate(true);
        } else if (command.equals("calibrateOff")){
            displayMessage("Calibrate OFF");
            sp.calibrate(false);
        } else if (command.equals("sleep")) {
            displayMessage("goodnight");
            sp.sleep();
        } else if (command.equals("setBackLED")) {
            int value = ((Long) data.get("value")).intValue();
            displayMessage("Set Back LED: value=" + value);
            sp.setBackLED(value);
        } else if (command.equals("setCollisionDetection")) {
            boolean value = (Boolean) data.get("value");
            displayMessage("Enable Collision Detection: " + value);
            sp.enableCollisionDetection(value);

            if (value) {
                final WebSocket connection = conn;

                Sphero.SpheroHandler onCollide = new Sphero.SpheroHandler() {
                    public void handle() {
                        JSONObject response = new JSONObject();
                        response.put("collision", true);
                        connection.send(response.toJSONString());
                    }

                    public void handle(byte data) {
                    }
                };
                sp.setCollisionHandler(onCollide);
            } else {
                sp.setCollisionHandler(null);
            }
        } else if (command.equals("clear")) {
            //System.out.println("clear");
            sp.clearPacketNum();
            //TODO:: how to clear the screen? how will this app run?
        } else {
            displayMessage("TODO: " + command);
        }
    }

    public void displayMessage(String message) {
        commandPrompt.printToCommandPrompt(message, Color.BLACK);
    }

    public void displayError(String error) {
        commandPrompt.printToCommandPrompt(error, Color.RED);
    }

    @Override
    public void onError(WebSocket conn, Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        displayError(sw.toString());
    }

    //Seperate functions to deal with larger responses to certain messages
    public JSONArray listDevices(JSONObject data) {
        displayMessage("searching for spheros...");
        try {
            sp_devices = sp.findSpheros(true);

            JSONArray jsonDevices = new JSONArray();
            if (sp_devices.size() == 0) {
                displayMessage("no spheros found.");
                displayMessage("Have you paired one to your computer through the Bluetooth Manager?");
            } else displayMessage("found spheros:");
            for (Iterator i = sp_devices.iterator(); i.hasNext(); ) {
                RemoteDevice d = (RemoteDevice) i.next();
                JSONObject jsonDevice = new JSONObject();
                String name = d.getFriendlyName(false);
                String address = d.getBluetoothAddress();
                jsonDevice.put("name", name);
                jsonDevice.put("address", formatAddress(address));
                displayMessage("name: " + name + ", address: " + address);
                jsonDevices.add(jsonDevice);
            }

            return jsonDevices;
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            displayError(sw.toString());
            return null;
        }
    }

    public JSONObject connectToDevice(JSONObject data) {
        String name = (String) data.get("name");
        String address = (String) data.get("address");
        address = unformatBluetoothAddress(address);

        JSONObject json = new JSONObject();
        if (sp.connect(address)) {
            json.put("connected", true);
            displayMessage("Connection successful");
        } else {
            json.put("connected", false);
            displayMessage("Connection NOT successful");
        }
        return json;
    }

    public String formatAddress(String ba) {
        String address = "";
        for (int i = 0; i < ba.length(); i += 2) {
            if (i != 0)
                address += ":";
            address += ba.substring(i, i + 2);
        }
        return address;
    }

    public String unformatBluetoothAddress(String address) {
        String ba = "";
        for (int i = 0; i < address.length(); i++) {
            if (address.charAt(i) != ':')
                ba += address.charAt(i);
        }
        return ba;
    }
}
