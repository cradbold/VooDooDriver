/*
Copyright 2011-2012 SugarCRM Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
Please see the License for the specific language governing permissions and
limitations under the License.
*/

package org.sugarcrm.voodoodriver;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.FilenameUtils;

public class Reporter {

   private String resultDir = "";
   private String reportLog = null;
   private FileOutputStream reportFD = null;
   private int Blocked = 0;
   private int Exceptions = 0;
   private int FailedAsserts = 0;
   private int PassedAsserts = 0;
   private int OtherErrors = 0;
   private int WatchDog = 0;
   private String LineSeparator = null;
   private Browser browser = null;
   private boolean isRestart = false;
   private String testName = null;

   /**
    * When to save the current HTML page.  Keys are the possible
    * events, and the values are true or false.
    */

   private VDDHash saveHtmlOn;

   /**
    * Saved HTML page file name index.
    */

   private int saveHtmlIdx = 0;

   /**
    * When to take a screenshot.  Keys are the possible events, and
    * the values are true or false.
    */

   private VDDHash screenshotOn;

   /**
    * Screenshot file name index.
    */

   private int screenshotIdx = 0;


   /**
    * Instantiate a Reporter object.
    *
    * @param reportName
    * @param resultDir
    */

   public Reporter(String reportName, String resultDir) {
      Date now = new Date();
      String frac = String.format("%1$tN", now);
      String date_str = String.format("%1$tm-%1$td-%1$tY-%1$tI-%1$tM-%1$tS", now);
      frac = frac.subSequence(0, 3).toString();
      date_str += String.format("-%s", frac);

      this.LineSeparator = System.getProperty("line.separator");

      if (resultDir != null) {
         File dir = new File(resultDir);
         if (!dir.exists()) {
            dir.mkdirs();
         }

         this.resultDir = resultDir;
      } else {
         this.resultDir = System.getProperty("user.dir");
      }

      reportLog = this.resultDir + "/" + reportName + "-" + date_str + ".log";
      reportLog = FilenameUtils.separatorsToSystem(reportLog);
      System.out.printf("ReportFile: %s\n", reportLog);

      try {
         reportFD = new FileOutputStream(reportLog);
      } catch (Exception exp) {
         exp.printStackTrace();
      }

      /* Initialize screenshot and savehtml events */
      String ssEvents[] = {"warning", "error", "assertfail", "exception",
                           "watchdog"};
      this.saveHtmlOn = new VDDHash();
      this.screenshotOn = new VDDHash();
      for (String ssEvent: ssEvents) {
         this.saveHtmlOn.put(ssEvent, false);
         this.screenshotOn.put(ssEvent, false);
      }
   }

   public void setTestName(String name) {
      this.testName = name;
   }

   public void setIsRestTest(boolean restart) {
      this.isRestart = restart;
   }

   public void setBrowser(Browser browser) {
      this.browser = browser;
   }


   /**
    * Set the events for saving the current HTML page.
    *
    * Input is expected to a string that is either "all" or a
    * comma-separated list of events.  Valid events are:
    *
    * <ul><li>warning</li>
    *     <li>error</li>
    *     <li>assertfail</li>
    *     <li>exception</li>
    *     <li>watchdog</li></ul>
    *
    * @param events  list of events
    */

   public void setSaveHTML(String events) {
      if (events.equals("all")) {
         for (String key: this.saveHtmlOn.keySet()) {
            this.saveHtmlOn.put(key, true);
         }
      } else {
         for (String event: events.split(",")) {
            if (!this.saveHtmlOn.containsKey(event)) {
               System.out.println("(!)Unrecognized event in savehtml list: " +
                                  event);
               continue;
            }
            this.saveHtmlOn.put(event, true);
         }
      }
   }


   /**
    * Set the events for taking a screenshot.
    *
    * Input is expected to a string that is either "all" or a
    * comma-separated list of events.  Valid events are:
    *
    * <ul><li>warning</li>
    *     <li>error</li>
    *     <li>assertfail</li>
    *     <li>exception</li>
    *     <li>watchdog</li></ul>
    *
    * @param events  list of events
    */

   public void setScreenshot(String events) {
      if (events.equals("all")) {
         for (String key: this.screenshotOn.keySet()) {
            this.screenshotOn.put(key, true);
         }
      } else {
         for (String event: events.split(",")) {
            if (!this.screenshotOn.containsKey(event)) {
               System.out.println("(!)Unrecognized event in screenshot list: " +
                                  event);
               continue;
            }
            this.screenshotOn.put(event, true);
         }
      }
   }


   public String getLogFileName() {
      return this.reportLog;
   }

   public TestResults getResults() {
      TestResults result = null;
      Integer res = 0;

      result = new TestResults();
      result.put("testlog", this.reportLog);
      result.put("blocked", this.Blocked);
      result.put("exceptions", this.Exceptions);
      result.put("failedasserts", this.FailedAsserts);
      result.put("passedasserts", this.PassedAsserts);
      result.put("watchdog", this.WatchDog);
      result.put("errors", this.OtherErrors);
      result.put("isrestart", this.isRestart);

      if (this.Blocked > 0 || this.Exceptions > 0 || this.FailedAsserts > 0 || this.OtherErrors > 0) {
         res = -1;
      }

      result.put("result", res);

      return result;
   }

   private String replaceLineFeed(String str) {
      str = str.replaceAll("\n", "\\\\n");
      return str;
   }

   private void _log(String msg) {
      Date now = new Date();
      String frac = String.format("%1$tN", now);
      String date_str = String.format("%1$tm/%1$td/%1$tY-%1$tI:%1$tM:%1$tS", now);

      frac = frac.subSequence(0, 3).toString();
      date_str += String.format(".%s", frac);

      msg = replaceLineFeed(msg);
      String logstr = "[" + date_str + "]" + msg + this.LineSeparator;

      if (msg.isEmpty()) {
         msg = "Found empty message!";
      }

      try {
         this.reportFD.write(logstr.getBytes());
         System.out.printf("%s\n", msg);
      } catch (Exception exp) {
         exp.printStackTrace();
      }
   }

   public void closeLog() {
      try {
         this.reportFD.close();
         this.reportFD = null;
      } catch (Exception exp) {
         exp.printStackTrace();
      }
   }

   public void Log(String msg) {
      this._log("(*)" + msg);
   }

   public void Warn(String msg) {
      this._log("(W)" + msg);

      if ((Boolean)this.saveHtmlOn.get("warning")) {
         this.SavePage();
      }
      if ((Boolean)this.screenshotOn.get("warning")) {
         this.screenshot();
      }
   }

   public void ReportError(String msg) {
      this._log(String.format("(!)%s", msg));
      this.OtherErrors += 1;

      if ((Boolean)this.saveHtmlOn.get("error")) {
         this.SavePage();
      }
      if ((Boolean)this.screenshotOn.get("error")) {
         this.screenshot();
      }
   }

   public void ReportWatchDog() {
      this.WatchDog = 1;

      if ((Boolean)this.saveHtmlOn.get("watchdog")) {
         this.SavePage();
      }
      if ((Boolean)this.screenshotOn.get("watchdog")) {
         this.screenshot();
      }
   }

   public void ReportBlocked() {
      this.Blocked = 1;
   }


   /**
    * Log the exception only.
    *
    * This helper routine is needed since some of the Reporter methods
    * could need to report an exception.
    *
    */

   private void justReportTheException(Exception e) {
      this.Exceptions += 1;
      String msg = "--Exception Backtrace: ";
      StackTraceElement[] trace = e.getStackTrace();
      String message = "";

      if (e.getMessage() != null) {
         String[] msg_lines = e.getMessage().split("\\n");
         for (int i = 0; i <= msg_lines.length -1; i++) {
            message += msg_lines[i] + "  ";
         }

         this._log("(!)Exception raised: " + message);

         for (int i = 0; i <= trace.length -1; i++) {
            String tmp = trace[i].toString();
            msg += "--" + tmp;
         }
      } else {
         msg = "ReportException: Exception message is null!!!";
         e.printStackTrace();
      }

      this._log("(!)" + msg);
   }


   /**
    * Log an exception.
    *
    * This method formats a java exception into a log entry.  Both the
    * message and the stack trace are reformatted and printed to the
    * SODA log file and the console.
    *
    * @param e  the exception to report
    */

   public void ReportException(Exception e) {
      justReportTheException(e);

      if ((Boolean)this.saveHtmlOn.get("exception")) {
         this.SavePage();
      }
      if ((Boolean)this.screenshotOn.get("exception")) {
         this.screenshot();
      }
   }


   public boolean isRegex(String str) {
      boolean result = false;
      Pattern p = Pattern.compile("^\\/");
      Matcher m = p.matcher(str);

      p = Pattern.compile("\\/$|\\/\\w+$");
      Matcher m2 = p.matcher(str);

      if (m.find() && m2.find()) {
         result = true;
      } else {
         result = false;
      }

      return result;
   }

   public String strToRegex(String val) {
      String result = "";
      val = val.replaceAll("\\\\", "\\\\\\\\");
      val = val.replaceAll("^/", "");
      val = val.replaceAll("/$", "");
      val = val.replaceAll("/\\w$", "");
      result = val;
      return result;
   }

   public boolean Assert(String msg, boolean state, boolean expected) {
      boolean result = false;
      String status = "";

      if (state == expected) {
         this.PassedAsserts += 1;
         status = "(*)Assert Passed: ";
         result = true;
      } else {
         this.FailedAsserts += 1;
         status = "(!)Assert Failed: ";
         result = false;
      }

      status = status.concat(msg);
      this._log(status);

      if (result == false && (Boolean)this.saveHtmlOn.get("assertfail")) {
         this.SavePage();
      }
      if (result == false && (Boolean)this.screenshotOn.get("assertfail")) {
         this.screenshot();
      }

      return result;
   }

   public boolean Assert(String value, String src) {
      boolean result = false;
      String msg = "";

      if (isRegex(value)) {
         value = this.strToRegex(value);
         Pattern p = Pattern.compile(value, Pattern.MULTILINE);
         Matcher m = p.matcher(src);
         if (m.find()) {
            this.PassedAsserts += 1;
            msg = String.format("Assert Passed, Found: '%s'.", value);
            this.Log(msg);
            result = true;
         } else {
            this.FailedAsserts += 1;
            msg = String.format("(!)Assert Failed for find: '%s'!", value);
            this._log(msg);
            result = false;
         }
      } else {
         if (src.contains(value)) {
            this.PassedAsserts += 1;
            msg = String.format("Assert Passed, Found: '%s'.", value);
            this.Log(msg);
            result = true;
         } else {
            this.FailedAsserts += 1;
            msg = String.format("(!)Assert Failed for find: '%s'!", value);
            this._log(msg);
            result = false;
         }
      }

      if (result == false && (Boolean)this.saveHtmlOn.get("assertfail")) {
         this.SavePage();
      }
      if (result == false && (Boolean)this.screenshotOn.get("assertfail")) {
         this.screenshot();
      }

      return result;
   }

   public boolean AssertNot(String value, String src) {
      boolean result = false;
      String msg = "";

      if (isRegex(value)) {
         value = this.strToRegex(value);
         if (src.matches(value)) {
            this.FailedAsserts += 1;
            msg = String.format("(!)Assert Failed, Found Unexpected text: '%s'.", value);
            this._log(msg);
            result = false;
         } else {
            this.PassedAsserts += 1;
            msg = String.format("Assert Passed did not find: '%s' as expected.", value);
            this.Log(msg);
            result = true;
         }
      } else {
         if (src.contains(value)) {
            this.FailedAsserts += 1;
            msg = String.format("(!)Assertnot Failed: Found: '%s'.", value);
            this._log(msg);
            result = false;
         } else {
            this.PassedAsserts += 1;
            msg = String.format("Assert Passed did not find: '%s' as expected.", value);
            this.Log(msg);
            result = true;
         }
      }

      if (result == false && (Boolean)this.saveHtmlOn.get("assertfail")) {
         this.SavePage();
      }
      if (result == false && (Boolean)this.screenshotOn.get("assertfail")) {
         this.screenshot();
      }

      return result;
   }


   /**
    * Create a file name.
    *
    * Use the specified directory, file name root, and file index to
    * create the filename.  The directory is assumed to be relative to
    * resultDir.
    *
    * @param dir   the directory in which to create the file name
    * @param file  the file name root
    * @param idx   one-up index of this file
    * @param ext   file extension
    * @return path and file name
    */

   private String makeFilename(String dir, String file, int idx, String ext) {
      String test = "";

      String outfile = this.resultDir + "/" + dir;

      File checkDir = new File(outfile);
      if (!checkDir.exists()) {
         checkDir.mkdir();
      }

      if (this.testName != null) {
         File tmp = new File(this.testName);
         test = tmp.getName();
         test = String.format("%s-", test.substring(0, test.length() - 4));
      }

      outfile += String.format("/%s%s-%d.%s", test, file, idx, ext);

      return FilenameUtils.separatorsToSystem(outfile);
   }


   /**
    * Save the current HTML page.
    */

   public void SavePage() {
      String htmlFile = makeFilename("saved-html", "savedhtml",
                                     this.saveHtmlIdx, "html");
      this.saveHtmlIdx += 1;

      String pageSource = this.browser.getPageSource();

      try {
         File f = new File(htmlFile);
         BufferedWriter bw = new BufferedWriter(new FileWriter(f));
         bw.write(pageSource);
         bw.close();
         this.Log(String.format("HTML Saved: %s", htmlFile));
      } catch (java.io.IOException e) {
         this.justReportTheException(e);
      }
   }


   /**
    * Take a screenshot of the current page.
    */

   public void screenshot() {
      String screenshotFile = makeFilename("screenshots", "screenshot",
                                           this.screenshotIdx, "png");
      this.screenshotIdx += 1;

      Utils.takeScreenShot(screenshotFile, this, false);
   }


   /**
    * Clean up on object destruction.
    *
    * This method simply makes sure that the output file handle is
    * properly closed.
    */

   protected void finalize() throws Throwable {
       try {
          if (this.reportFD != null) {
             this.reportFD.close();
          }
       } finally {
           super.finalize();
       }
   }
}
