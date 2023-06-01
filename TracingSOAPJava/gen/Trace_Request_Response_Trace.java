package gen;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;

import com.ibm.broker.javacompute.MbJavaComputeNode;
import com.ibm.broker.plugin.MbElement;
import com.ibm.broker.plugin.MbException;
import com.ibm.broker.plugin.MbMessage;
import com.ibm.broker.plugin.MbMessageAssembly;
import com.ibm.broker.plugin.MbOutputTerminal;
import com.ibm.broker.plugin.MbXMLNSC;

/**
 * @author Douglas Leekam
 * This class contains the methods needed to start and stop a 
 * IBM integration bus user trace in order to debug the flow 
 * without having to interact with the cli, this is useful 
 * in those cases where the developer doesn't have access to the environment
 */
public class Trace_Request_Response_Trace extends MbJavaComputeNode {

	public void evaluate(MbMessageAssembly inAssembly) throws MbException {
		MbOutputTerminal out = getOutputTerminal("out");

		MbMessage inMessage = inAssembly.getMessage();
		MbMessageAssembly outAssembly = null;
		String OS = System.getProperty("os.name").toLowerCase();

		try {
			// create new message as a copy of the input
			MbMessage outMessage = new MbMessage();
			outAssembly = new MbMessageAssembly(inAssembly, outMessage);
			// ----------------------------------------------------------
			// Add user code below
			String brokerName 	 = inMessage.getRootElement().getFirstElementByPath("/XMLNSC/Trace/TraceRequest/BrokerName").getValueAsString();
			String ExcutionGroup = inMessage.getRootElement().getFirstElementByPath("/XMLNSC/Trace/TraceRequest/ExcutionGroup").getValueAsString();
			String signal 		 = inMessage.getRootElement().getFirstElementByPath("/XMLNSC/Trace/TraceRequest/signal").getValueAsString().toUpperCase();
			
			String pathTrace =  FilePath(brokerName)+OSSeparator(OS);
			
			outAssembly = new MbMessageAssembly(inAssembly, outMessage);
			MbElement outBody= outMessage
					.getRootElement()
					.createElementAsLastChild(MbXMLNSC.PARSER_NAME)
					.createElementAsLastChild(MbXMLNSC.FOLDER,"TraceResponse",null);
			
			if (signal.equals("START")){
				// start the trace globally on the iib, sometimes needed in order to get the traces with the trace nodes
				createOutput(outBody,"mqsichangetrace "+brokerName+" -t -b -l debug", true); 
				
				// start the trace on the Execution group, sometimes needed in order to get the traces with the trace nodes
				createOutput(outBody,"mqsichangetrace "+brokerName+" -n on -e "+ ExcutionGroup, true);
				
				// Specifies the kind of trace that is goin to be started
				createOutput(outBody,"mqsichangetrace "+brokerName+" -u -e "+ ExcutionGroup + " -l debug -r ", true);
			}else if (signal.equals("STOP")){
				
				// Read the trace from the ESB.EG and write on a file on the server
				createOutput(outBody,"mqsireadlog " +  brokerName + " -u -e "+ ExcutionGroup + " -f -o " + pathTrace +ExcutionGroup + ".xml", true);
				
				// Format the output trace on xml to txt, so it will be human readble
				createOutput(outBody,"mqsiformatlog " + "-i "+ pathTrace + ExcutionGroup + ".xml " + " -o "+ pathTrace + ExcutionGroup + ".txt" , true);
				
				// Stop the trace on the broker globally and on the eg.
				createOutput(outBody,"mqsichangetrace "+brokerName+" -t -b -l none", true);
				createOutput(outBody,"mqsichangetrace " +  brokerName + "  -n off -e "+ ExcutionGroup, true);
				createOutput(outBody,pathTrace + ExcutionGroup + ".txt", readTraceFile(pathTrace + ExcutionGroup + ".txt" ));
			}
			else{
				MbElement value = outBody.createElementAsLastChild(MbXMLNSC.FOLDER, "item", null);
				value.createElementAsLastChild(MbXMLNSC.FIELD, "command","");
				value.createElementAsLastChild(MbXMLNSC.FIELD, "Result","Incorrect command, must be \n\t\t\t\t -start \n\t\t\t\t -stop ");
			}

			if (signal.equals("STOP")&&isUnix(OS)){
				deleteTrace(DeleteCommand(OS) + " " + pathTrace + ExcutionGroup + ".txt" );
				deleteTrace(DeleteCommand(OS) + " " + pathTrace + ExcutionGroup + ".xml");
			}
			

			// End of user code
			// ----------------------------------------------------------
		} catch (Exception e) {
			// Re-throw to allow Broker handling of MbException
			MbMessage outMessage = new MbMessage();
			outAssembly = new MbMessageAssembly(inAssembly, outMessage);
			MbElement outXmlnsc = outMessage.getRootElement().createElementAsLastChild(MbXMLNSC.PARSER_NAME);
			outXmlnsc.createElementAsLastChild(MbXMLNSC.FOLDER, "TraceResponse", getStackTrace(e));

		} 
		// The following should only be changed
		// if not propagating message to the 'out' terminal
		System.gc();
		out.propagate(outAssembly);

	}
	
	/**
	 * Method needed to get the trace file
	 * @author Douglas Leekam
	 * @param fileName (String) path of the file that is going to be returned in case of stop trace
	 * @throws IOException 
	 * @return (String) The content of the file
	 * */
	private String readTraceFile(String fileName) throws IOException{
		@SuppressWarnings("resource")
		BufferedReader bufferedReader = new BufferedReader(new FileReader(fileName));
		StringBuffer stringBuffer = new StringBuffer();
		String line = null;
		while((line =bufferedReader.readLine())!=null){
			stringBuffer.append(line).append("\n");
		}
		return stringBuffer.substring(0);
	}
	
	/**
	 * Method used to generate the array of items for the output (overrided to invoke a cli command)
	 * @author Douglas Leekam
	 * @param output (MbElement) Output Assembly to generate internally
	 * @param command (String) command that is going to be executed on the cli
	 * @param broker (MbElement) indicates to the invokeCommand(String,boolean) whether wait for a response or not
	 * @throws Exception
	 * */
	public void createOutput(MbElement output, String command, boolean broker) throws Exception{
		MbElement value = output.createElementAsLastChild(MbXMLNSC.FOLDER, "item", null);
		value.createElementAsLastChild(MbXMLNSC.FIELD, "command",command);
		value.createElementAsLastChild(MbXMLNSC.FIELD, "Result",invokeCommand(command,broker));
	}
	
	/**
//	 * Method used to generate the array of items for the output (overrided to add a string on the result field)
	 * @author Douglas Leekam
	 * @param output (MbElement) Output Assembly to generate internally
	 * @param info (String) information related to the result
	 * @param result (String) information that needs to be informed
	 * @throws Exception
	 * */
	public void createOutput(MbElement output, String info, String result) throws Exception{
		MbElement value = output.createElementAsLastChild(MbXMLNSC.FOLDER, "item", null);
		value.createElementAsLastChild(MbXMLNSC.FIELD, "command",info);
		value.createElementAsLastChild(MbXMLNSC.FIELD, "Result",result);
	}
	
	
	/**
	 * Method used to get the stacktrace on string in order to debug on the trace of iib.
	 * @author Douglas Leekam
	 * @param exception (Exception) exception happened on runtime.
	 * @return stacktrace (String) The stacktrace that was throw on run time.
	 */
    public String getStackTrace(Exception exception) {
    	StringWriter writer = new StringWriter();
        PrintWriter printWriter= new PrintWriter(writer);
        exception.printStackTrace(printWriter);
    	return writer.toString();
    } 
    
    /**
     * Method that returns if the runtime is on windows or not.
     * @author Douglas Leekam
     * @param OS (String) Operative System gotten from the cli
     * @return iswindows (Boolean) returns true if the application is running on windows or false if not
     */
	public static boolean isWindows(String OS) {
		return (OS.indexOf("win") >= 0);
	}

	
    /**
     * Method that returns if the runtime is on mac or not.
     * @author Douglas Leekam
     * @param OS (String) Operative System gotten from the cli
     * @return iswindows (Boolean) returns true if the application is running on mac or false if not
     */
	public static boolean isMac(String OS) {
		return (OS.indexOf("mac") >= 0);
	}

	/**
     * Method that returns if the runtime is on unix or not.
     * @author Douglas Leekam
     * @param OS (String) Operative System gotten from the cli
     * @return iswindows (Boolean) returns true if the application is running on unix or false if not
     */
	public static boolean isUnix(String OS) {
		return (OS.indexOf("nix") >= 0 || OS.indexOf("nux") >= 0 || OS.indexOf("aix") >= 0 );
	}

	
	/**
	 * Method that returns the file path of the broker based on the cli response
	 * @author Douglas Leekam
	 * @param BrokerName (String) Name of the broker
	 * @return value (String) file path of the broker
	 * @throws Exception
	 */
	public static String FilePath(String BrokerName) throws Exception{
		String[] parts = invokeCommand( "mqsireportbroker " + BrokerName, false)
				.split("\n");
		String value = parts[4]
				.substring(parts[4].lastIndexOf("=") + 1)
				.replace("\'", "")
				.replaceAll("^\\s+", "")
				.replaceAll("\\s+$", "");
		return value;
	}
	
	
	/**
	 * Method that returns the result of a command executed on the cli.
	 * @author Douglas Leekam
	 * @param strCmd (String) command to be executed
	 * @param broker (Boolean) indicates if we need to wait the command to finish
	 * @return (String) result the result of the command
	 * @throws Exception
	 */
	public static String invokeCommand(String strCmd, boolean broker) throws Exception{
		String resultString = "";
		StringBuilder sb= new StringBuilder("");
		Process process =Runtime.getRuntime().exec(strCmd);
		BufferedReader br = null; 
		if (broker) {
			 br = new BufferedReader(new InputStreamReader(process.getInputStream(), "US-ASCII"));
		}else{
			 br = new BufferedReader(new InputStreamReader(process.getInputStream()));
		}
		while ((resultString = br.readLine()) != null){
			if (resultString.contains("BIP") && broker) {
				process.waitFor();
		        process.destroy();
				return resultString;
			}
			else {
				sb.append("\n"+resultString);
			}
		}
		
		if (!broker)
			resultString = sb.toString();
		
		process.waitFor();
        process.destroy();
        
		return strCmd+" "+resultString;
	}
	
	/**
	 * Method that deletes the output trace file
	 * @author Douglas leekam
	 * @param filename (String) Filename to be deleted
	 * @throws Exception
	 */
	private void deleteTrace(String filename)throws Exception{
		Process process =Runtime.getRuntime().exec(filename);
		process.waitFor();
        process.destroy();
	}
	/**
	 * Method that determines what command is going to be used in order to delete a file
	 * @author Douglas leekam
	 * @param OS (String) Operative System where the application is hosted
	 * @return (String) the command to delete a file
	 */
	public static String DeleteCommand(String OS){
		if (isWindows(OS))
			return "del /f";
		if (isUnix(OS))
			return "rm ";
		return "-";
	}	
	
	/** 
	 * Method to determine what FileSystem Separator to use
	 * @author Douglas leekam
	 * @param OS (String)  Operative System where the application is hosted 
	 * @return (String) FileSystem separator 
	 */
	public static String OSSeparator(String OS){
		if (isWindows(OS))
			return "\\";
		if (isUnix(OS))
			return "/";
		return "-";
	}
}
