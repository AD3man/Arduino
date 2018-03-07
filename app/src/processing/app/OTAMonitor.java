package processing.app;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.NoRouteToHostException;
import java.net.Socket;

import cc.arduino.packages.BoardPort;

/**
 * @author Anže Dežman (AD3man) <br><br>
 * 
 * 
 * This class connects to the OTASerial text server running on the OTA enabled board.
 * It reads server's output and then displays it on the console.<br><br> 
 * 
 * This class and other additions were made to be used with OTASerial library <br>
 *  https://github.com/AD3man/ESP8266_OTASerial<br><br>
 * 
 * OTAMonitor is instantiated in 'cc.arduino.packages.MonitorFactory'.<br><br>
 *  
 * When instantiated a Thread is started inside which connection to the text server 
 * is established then output reading and displaying takes place.<br>
 * 
 * The thread should close when the console GUI is closed.<br><br>
 *  
 * How it works:<br>
 * <br>
 *  If a board is connected over a network port via OTA (ArduinoOTA) 
 *   and OTASerial library is present and running on that board,
 *    pressing on "Serial monitor" will bring up OTAMonitor.<br>
 * 
 * <br><br>
 * For this class to work additions were made in folowing files:<br>
 * 
 * - cc.arduino.packages.MonitorFactory
 *     - in method newMonitor(BoardPort port)<br><br>
 * 
 * - cc.arduino.packages.discoverers.NetworkDiscovery 
 *     - in method serviceResolved(ServiceEvent serviceEvent)<br><br>
 * 
 * 
 */
public class OTAMonitor extends AbstractTextMonitor implements Runnable {
 
  private String ip = null;
  private int port = 23;
  volatile private boolean run = false;
  private static boolean debug = true;
  

  private Socket printSocket = null;
  private PrintWriter sender = null;
  private Thread worker = null;

  public OTAMonitor(BoardPort port, int debugServerPort) {
    super(port);

    this.serialRates.setVisible(false);
    this.serialRates = null;

    this.ip = port.getAddress();
    this.port = debugServerPort;
    this.worker = new Thread(this);
    this.run = true;
    this.worker.start();

    this.onSendCommand(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        send(textField.getText());
        textField.setText("");
      }
    });
    this.onClearCommand(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        textArea.setText("");
      }
    });
    this.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        run = false;
        waitForEnable = false;
      }
    });

  }

  volatile private boolean waitForEnable = false;

  protected void onEnableWindow(boolean enable) {

    textArea.setEnabled(enable);
    clearButton.setEnabled(enable);
    scrollPane.setEnabled(enable);
    textField.setEnabled(enable);
    sendButton.setEnabled(enable);
    autoscrollBox.setEnabled(enable);
    lineEndings.setEnabled(enable);

    if (enable) {
      if (waitForEnable) {
        debugPrintln("[DEBUG: ENABLING WINDOW");
        run = true;
        waitForEnable = false;
      }
    } else {
      debugPrintln("[DEBUG: stopping worker - disabled window");
      waitForEnable = true;
      run = false;
    }
  }

  public static void debugPrintln(String s) {
    if(debug)System.out.println(s);
  }

  private void send(String s) {
    try {
      switch (lineEndings.getSelectedIndex()) {
      case 1:
        s += "\n";
        break;
      case 2:
        s += "\r";
        break;
      case 3:
        s += "\r\n";
        break;
      default:
        break;
      }
      sender.print(s);
      sender.flush();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public boolean hasServer = false;

  @Override
  public void run() {

    while ((waitForEnable || run) ) {
      while (waitForEnable) {
        try {
          Thread.sleep(200);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
      if(!run) return;
      debugPrintln("[RESTARTING CONNECTION]");
      BufferedReader received = null;
        
      try {
        
        printSocket = new Socket(ip, port);
        sender = new PrintWriter(printSocket.getOutputStream(), true);
        received = new BufferedReader(
            new InputStreamReader(printSocket.getInputStream()));

        
        String OTASerialHello = received.readLine();
        /*
        *  Here a simple server - client authentication takes place.
        *  Server sends a 'hello' message "HELLO:OTASERIAL" and OTAMonitor responds with "HELLO:ARDUINO:OTAMONITOR".
        *  "RESPONSE:OK" from the server verifies the authentication.
        *   
        */
        if (OTASerialHello.equals("HELLO:OTASERIAL")) {
         
          sender.println("HELLO:ARDUINO:OTAMONITOR");
          
          String resp = received.readLine();
          if (resp.equals("RESPONSE:OK") ) {
            hasServer = true;
          } else {
         // OTAMonitor could not connect.
            hasServer = false;
            run = false;
            System.err
            .println("Wrong \"RESPONSE\" message received from OTASerial server at " + ip + ":" + port
                     + ".\n        "
                     + "Check if device or OTASerial library are operating correctly.\n        "
                     + "Restart Serial monitor to try again");
          }
        } else if(OTASerialHello.substring(0, 4).equals("FULL")) {
          message("An OTAMonitor on IP " + OTASerialHello.split(":")[1] + " is already connected to this board.");
          hasServer = false;
          run = false;
        } else {
          // OTAMonitor received wrong 'hello' message.
          hasServer = false;
          run = false;
          System.err
          .println("Wrong \"hello\" message received from OTASerial server at " + ip + ":" + port
                   + ".\n        "
                   + "Check if device or OTASerial library are operating correctly.\n        "
                   + "Restart Serial monitor to try again");
        }
      } catch (NoRouteToHostException e) {
        System.err
            .println("OTASerial at address " + ip + ":" + port
                     + " is not reachable. \n        "
                     + "Check if device or OTASerial library are operating correctly.\n        "
                     + "Restart Serial monitor to try again");
        hasServer = false;
        run = false;
      } catch (IOException e) {
        System.err.println("An I/O error has occured while trying to read data from "
                           + "your board at " + ip + ":"
                           + port  + ".\n"
                           + "OTAMonitor will try now to reconnect.");
        hasServer = false;

      }

      
      char buffer[] = new char[256];
      /*
       * Here output from the text server is read and displayed on the console.
       * */
      while (run && hasServer) {
        try {
          int l = received.read(buffer);
           
          if (l >= 0) {
            message("" + new String(buffer, 0, l));
          }else {
            Thread.sleep(1);
          } 
        } catch (IOException e1) {
          e1.printStackTrace();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
      try {
        if (received != null) received.close();
        if (sender != null) sender.close();
        if (printSocket != null) printSocket.close();
      } catch (IOException e) {
        e.printStackTrace();
      }

      debugPrintln("[run is closing]");
    }
  }

}
