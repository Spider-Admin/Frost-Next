/*
Execute.java / Frost
Copyright (C) 2001  Frost Project <jtcfrost.sourceforge.net>

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License as
published by the Free Software Foundation; either version 2 of
the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
*/
package frost.ext;

import java.io.*;
import java.util.*;
import java.util.logging.*;

import frost.*;

/**
 * Supports execution of external programs
 * @author Jan-Thomas Czornack
 */
public class Execute {

    private static Logger logger = Logger.getLogger(Execute.class.getName());

    /**
     * start an external program, return immediately and forget any output
     * for running a file via dobbelklick
     * @param order the command to execute
     * 
     */
    public static void simple_run(String order[]) throws Throwable {
        logger.info("-------------------------------------------------------------------\n" +
                    "Execute: " + order + "\n" +
                    "-------------------------------------------------------------------");
        Runtime.getRuntime().exec(order);
    }
    
    
    /**
     * start an external program, wait for  and forget any output
     * @param order the command to execute
     * @return the output generated by the program. Standard ouput and Error output are captured.
     */
/*    public static void run_wait(String order) throws Throwable {
      
      ArrayList cmdList = new ArrayList();
      cmdList = order.split();
      run_wait1(cmdList);
      return; 
    }
*/    
    /**
     * start an external program, and return their output
     * @param order the command to execute
     * @return the output generated by the program. Standard ouput and Error output are captured.
     */
    public static List run_wait(String order) throws Throwable {
        logger.info("-------------------------------------------------------------------\n" +
                    "Execute: " + order + "\n" +
                    "-------------------------------------------------------------------");
        
        ArrayList result = new ArrayList();
        
      
        Process p = Runtime.getRuntime().exec(order);  // java 1.4 String Order
        //ProcessBuilder pb = new ProcessBuilder(order);   // java 1.5 List<String> order 
        //Process p = pb.start();
        
        InputStream stdOut = p.getInputStream();
        InputStream stdErr = p.getErrorStream();
  
        List tmpList;
        tmpList = FileAccess.readLines(stdOut, "UTF-8");
        if( tmpList != null ) {
            result.addAll(tmpList);
        }
        tmpList = FileAccess.readLines(stdErr, "UTF-8");
        if( tmpList != null ) {
            result.addAll(tmpList);
        }
        return result;
    }
}
