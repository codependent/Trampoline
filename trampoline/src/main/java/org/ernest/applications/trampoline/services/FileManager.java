package org.ernest.applications.trampoline.services;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.ernest.applications.trampoline.entities.Ecosystem;
import org.ernest.applications.trampoline.exceptions.CreatingMicroserviceScriptException;
import org.ernest.applications.trampoline.exceptions.CreatingSettingsFolderException;
import org.ernest.applications.trampoline.exceptions.ReadingEcosystemException;
import org.ernest.applications.trampoline.exceptions.RunningMicroserviceScriptException;
import org.ernest.applications.trampoline.exceptions.SavingEcosystemException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

@Component
public class FileManager {

	@Value("${settings.folder.path.mac}")
	private String settingsFolderPathMac;
	
	@Value("${settings.folder.path.linux}")
	private String settingsFolderPathLinux;
	
	@Value("${settings.folder.path.windows}")
	private String settingsFolderPathWindows;
	
	@Value("${settings.file.name}")
	private String settingsFileName;

	public Ecosystem getEcosystem() throws CreatingSettingsFolderException, ReadingEcosystemException {
		checkIfFileExistsAndCreatedIfNeeded();
		Ecosystem ecosystem = null;
		
		try {
			ecosystem = new Gson().fromJson(FileUtils.readFileToString(new File(getSettingsFolder() + "/" + settingsFileName)), Ecosystem.class);
		} catch (JsonSyntaxException | IOException e) {
			e.printStackTrace();
			throw new ReadingEcosystemException();
		}

		updateMicroservicesInformationStored(ecosystem);

		return ecosystem;
	}

	private void updateMicroservicesInformationStored(Ecosystem ecosystem) {
		if(ecosystem.getMicroservices().stream().anyMatch(m -> m.getActuatorPrefix() == null || m.getVmArguments() == null)){

			ecosystem.getMicroservices().stream().filter(m -> m.getActuatorPrefix() == null || m.getVmArguments() == null).forEach(m ->{
				m.setVmArguments("");
				m.setActuatorPrefix("");
				createScript(m.getId(), m.getPomLocation());
			});

			saveEcosystem(ecosystem);
		}
	}

	public void saveEcosystem(Ecosystem ecosystem) throws SavingEcosystemException {
		try {
			FileUtils.writeStringToFile(new File(getSettingsFolder() + "/" + settingsFileName), new Gson().toJson(ecosystem));
		} catch (IOException e) {
			e.printStackTrace();
			throw new SavingEcosystemException();
		}
	}

	public void runScript(String id, String mavenBinaryLocation, String mavenHomeLocation, String port, String vmArguments) throws RunningMicroserviceScriptException {
		try {
			mavenBinaryLocation = (mavenBinaryLocation != null && mavenBinaryLocation.trim().length() > 0) ? mavenBinaryLocation : mavenHomeLocation + "/bin";
			if(System.getProperties().getProperty("os.name").contains("Windows")){
				String commands = FileUtils.readFileToString(new File(getSettingsFolder() +"/"+ id +".txt"));
				commands = commands.replace("#mavenBinaryLocation", mavenBinaryLocation);
				commands = commands.replace("#mavenHomeLocation", mavenHomeLocation);
				commands = commands.replace("#port", port);
				commands = commands.replace("#vmArguments", vmArguments);
				Runtime.getRuntime().exec("cmd /c start cmd.exe /K \""+commands+"\"");

			}else{
				new ProcessBuilder("sh", getSettingsFolder() + "/" + id + ".sh", mavenHomeLocation, mavenBinaryLocation, port, vmArguments).start();
			}
			
		} catch (IOException e) {
			e.printStackTrace();
			throw new RunningMicroserviceScriptException();
		}
	}

	public void createScript(String id, String pomLocation) throws CreatingMicroserviceScriptException {
		try {
			if(System.getProperties().getProperty("os.name").contains("Windows")){
				FileUtils.writeStringToFile(new File(getSettingsFolder() +"/"+ id +".txt"), 
					"SET M2_HOME=#mavenHomeLocation&& SET PATH=%PATH%;#mavenBinaryLocation&& cd " + pomLocation + " && mvn spring-boot:run -Dserver.port=#port "
					+ "-Dendpoints.shutdown.enabled=true -Dmanagement.security.enabled=false #vmArguments");
			}else{
				FileUtils.writeStringToFile(new File(getSettingsFolder() +"/"+ id +".sh"),
					"export M2_HOME=$1; export PATH=$PATH:$2; cd " + pomLocation + "; mvn spring-boot:run -Dserver.port=$3 "
					+ "-Dendpoints.shutdown.enabled=true -Dmanagement.security.enabled=false $4");
			}
		} catch (IOException e) {
			e.printStackTrace();
			throw new CreatingMicroserviceScriptException();
		}
	}
	
	private String getSettingsFolder() {
		
		if(System.getProperties().getProperty("os.name").contains("Mac")){
			return settingsFolderPathMac.replaceAll("#userName", System.getProperties().getProperty("user.name"));
		}else if(System.getProperties().getProperty("os.name").contains("Windows")){
			return settingsFolderPathWindows;
		}else{
			return settingsFolderPathLinux.replaceAll("#userName", System.getProperties().getProperty("user.name"));
		}
	}

	private void checkIfFileExistsAndCreatedIfNeeded() throws CreatingSettingsFolderException {
		try{
			File file = new File(getSettingsFolder());
			if(!file.exists()){			
				file.mkdirs();
				FileUtils.writeStringToFile(new File(getSettingsFolder() + "/" + settingsFileName), new Gson().toJson(new Ecosystem()));
			}
		}catch(Exception e){
			e.printStackTrace();
			throw new CreatingSettingsFolderException();
		}
	}
}
