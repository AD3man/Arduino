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
 * Copyright 2015 Arduino LLC (http://www.arduino.cc/)
 */

package cc.arduino.packages;

import processing.app.AbstractMonitor;
import processing.app.NetworkMonitor;
import processing.app.OTAMonitor;
import processing.app.SerialMonitor;

public class MonitorFactory {

  public AbstractMonitor newMonitor(BoardPort port) {
    if ("network".equals(port.getProtocol())) {
      if ("yes".equals(port.getPrefs().get("ssh_upload"))) {
        // the board is SSH capable
        return new NetworkMonitor(port); 
        /*
         * The following lines are additions for OTAMonitor (processing.app.OTAMonitor) to work.
         * This is where OTAMonitor is instantiated.
         * 
         * Board property "OTA_Serial" is checked,
         *  if value is equal to "yes" then OTASerial is present on the board.
         * 
         * "OTA_Serial" property is added to the board in
         * 
         *    cc.arduino.packages.discoverers.NetworkDiscovery.serviceResolved
         * 
         * where service discovery data is parsed.
         * 
         * 
         * -AD3man
         * */
      } else if ("yes".equals(port.getPrefs().get("OTA_Serial"))) {
        String debug_port = port.getPrefs().get("OTA_Serial_port");
        return new OTAMonitor(port, Integer.parseInt(debug_port));
      } // End of additions - AD3man
      else {
        // SSH not supported, no monitor support
        return null;
      }
    }

    return new SerialMonitor(port);
  }

}
