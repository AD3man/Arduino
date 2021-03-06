/*
 * This file is part of Arduino.
 *
 * Arduino is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * As a special exception, you may use this file as part of a free software
 * library without restriction.  Specifically, if other files instantiate
 * templates or use macros or inline functions from this file, or you compile
 * this file and link it with other files to produce an executable, this
 * file does not by itself cause the resulting executable to be covered by
 * the GNU General Public License.  This exception does not however
 * invalidate any other reasons why the executable file might be covered by
 * the GNU General Public License.
 *
 * Copyright 2013 Arduino LLC (http://www.arduino.cc/)
 */

package cc.arduino.packages.discoverers;

import cc.arduino.packages.BoardPort;
import cc.arduino.packages.Discovery;
import processing.app.BaseNoGui;

import javax.jmdns.*;
import java.net.InetAddress;
import java.util.*;

import cc.arduino.packages.discoverers.network.BoardReachabilityFilter;

public class NetworkDiscovery implements Discovery, ServiceListener {

  private final List<BoardPort> reachableBoardPorts = new LinkedList<>();
  private final List<BoardPort> boardPortsDiscoveredWithJmDNS = new LinkedList<>();
  private Timer reachabilityTimer;
  private JmmDNS jmdns = null;

  private void removeDuplicateBoards(BoardPort newBoard) {
    synchronized (boardPortsDiscoveredWithJmDNS) {
      Iterator<BoardPort> iterator = boardPortsDiscoveredWithJmDNS.iterator();
      while (iterator.hasNext()) {
        BoardPort board = iterator.next();
        if (newBoard.getAddress().equals(board.getAddress())) {
          iterator.remove();
        }
      }
    }
  }

  @Override
  public void serviceAdded(ServiceEvent serviceEvent) {
  }

  @Override
  public void serviceRemoved(ServiceEvent serviceEvent) {
    String name = serviceEvent.getName();
    synchronized (boardPortsDiscoveredWithJmDNS) {
      boardPortsDiscoveredWithJmDNS.stream().filter(port -> port.getBoardName().equals(name)).forEach(boardPortsDiscoveredWithJmDNS::remove);
    }
  }

  @Override
  public void serviceResolved(ServiceEvent serviceEvent) {
    while (BaseNoGui.packages == null) {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    ServiceInfo info = serviceEvent.getInfo();
    for (InetAddress inetAddress : info.getInet4Addresses()) {
      String address = inetAddress.getHostAddress();
      String name = serviceEvent.getName();

      BoardPort port = new BoardPort();

      String board = null;
      String description = null;
      if (info.hasData()) {
        board = info.getPropertyString("board");
        description = info.getPropertyString("description");
        port.getPrefs().put("board", board);
        port.getPrefs().put("distro_version", info.getPropertyString("distro_version"));
        port.getPrefs().put("port", "" + info.getPort());

        //Add additional fields to permit generic ota updates
        //and make sure we do not intefere with Arduino boards
        // define "ssh_upload=no" TXT property to use generic uploader
        // define "tcp_check=no" TXT property if you are not using TCP
        // define "auth_upload=yes" TXT property if you want to use authenticated generic upload
        String useSSH = info.getPropertyString("ssh_upload");
        String checkTCP = info.getPropertyString("tcp_check");
        String useAuth = info.getPropertyString("auth_upload");
        if(useSSH == null || !useSSH.contentEquals("no")) useSSH = "yes";
        if(checkTCP == null || !checkTCP.contentEquals("no")) checkTCP = "yes";
        if(useAuth == null || !useAuth.contentEquals("yes")) useAuth = "no";
        port.getPrefs().put("ssh_upload", useSSH);
        port.getPrefs().put("tcp_check", checkTCP);
        port.getPrefs().put("auth_upload", useAuth);
        
        /*
		 * The following lines are additions for OTAMonitor (processing.app.OTAMonitor) to work.
		 * 
		 * In here values for "OTA_Serial" and "OTA_Serial_port" properties are processed.
		 * These values are embedded inside ArduinoOTA's MDNS response by the OTASerial library.
		 * 
		 * If OTASerial is not present on the board,
		 *  the values for these properties are set to "no".
		 * 
		 * Possible values:
		 * - "OTA_Serial": "yes", "no" otherwise;
		 * - "OTA_Serial_port": port number (0 - 65535) or "no".
		 * 
		 * 
		 * - AD3man
		 */
		String ota_ser = info.getPropertyString("OTA_Serial");
		String ota_ser_port;
		if (ota_ser == null || !ota_ser.equals("yes")) {
			ota_ser = "no";
			ota_ser_port = "no";
		}else {
			ota_ser_port = info.getPropertyString("OTA_Serial_port");
			if (ota_ser_port == null) {
				ota_ser = "no";
				ota_ser_port = "no";
			} else {
				try {
					// Can we parse ota_ser_port to an integer in the range of 0 to 65535? 
					// If not, that isn't a port number.
					int tmpPort = Integer.parseInt(ota_ser_port);
					if (tmpPort < 0 || tmpPort > 65535) {
						// Not a port number, therefore OTAMonitor wont work.
						// We set both properties to "no"
						ota_ser="no";
						ota_ser_port = "no";
					}
				} catch (NumberFormatException e212) {
					// Port number isn't even an integer.
					ota_ser="no";
					ota_ser_port = "no";
				}	
			}
		}
		//Here we store the values for the properties inside the board's preferences map
		port.getPrefs().put("OTA_Serial", ota_ser);
		port.getPrefs().put("OTA_Serial_port", ota_ser_port);
		
		
		// For debug
		/* 
		for (String k : port.getPrefs().keySet()) {
			System.out.println(k + ": " + port.getPrefs().get(k));
		};
		*/
		 
		// End of additions - AD3man.
        
        
      }

      String label = name + " at " + address;
      if (board != null && BaseNoGui.packages != null) {
        String boardName = BaseNoGui.getPlatform().resolveDeviceByBoardID(BaseNoGui.packages, board);
        if (boardName != null) {
          label += " (" + boardName + ")";
        }
      } else if (description != null) {
        label += " (" + description + ")";
      }

      port.setAddress(address);
      port.setBoardName(name);
      port.setProtocol("network");
      port.setLabel(label);

      synchronized (boardPortsDiscoveredWithJmDNS) {
        removeDuplicateBoards(port);
        boardPortsDiscoveredWithJmDNS.add(port);
      }
    }
  }

  public NetworkDiscovery() {

  }

  @Override
  public void start() {
    jmdns = JmmDNS.Factory.getInstance();
    jmdns.addServiceListener("_arduino._tcp.local.", this);
    reachabilityTimer =  new Timer();
    new BoardReachabilityFilter(this).start(reachabilityTimer);
  }

  @Override
  public void stop() {
    jmdns.unregisterAllServices();
    // we don't close the JmmDNS instance as it's too slow
    /*
    try {
      jmdns.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    */
    reachabilityTimer.cancel();
  }

  @Override
  public List<BoardPort> listDiscoveredBoards() {
      synchronized (reachableBoardPorts) {
      return new LinkedList<>(reachableBoardPorts);
    }
  }

  @Override
  public List<BoardPort> listDiscoveredBoards(boolean complete) {
    synchronized (reachableBoardPorts) {
      return new LinkedList<>(reachableBoardPorts);
    }
  }

  public void setReachableBoardPorts(List<BoardPort> newReachableBoardPorts) {
    synchronized (reachableBoardPorts) {
      this.reachableBoardPorts.clear();
      this.reachableBoardPorts.addAll(newReachableBoardPorts);
    }
  }

  public List<BoardPort> getBoardPortsDiscoveredWithJmDNS() {
    synchronized (boardPortsDiscoveredWithJmDNS) {
      return new LinkedList<>(boardPortsDiscoveredWithJmDNS);
    }
  }
}
