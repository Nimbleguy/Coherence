package org.tasgo.coherence.client;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModClassLoader;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.main.Main;
import net.minecraft.client.multiplayer.ServerAddress;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import org.apache.commons.io.FileDeleteStrategy;
import org.apache.commons.io.FileExistsException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.ClientProtocolException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.tasgo.coherence.Coherence;
import org.tasgo.coherence.Library;
import org.tasgo.coherence.ziputils.UnzipUtility;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import cpw.mods.fml.common.Loader;

@SideOnly(Side.CLIENT)
public class Cohere {
	private static final Logger logger = LogManager.getLogger("Coherence");
	public String url, address;
	public List<String> modlist;
	private List<String> neededmods;
	private File cohereDir;
	private String[] currentMods;
	private boolean updateConfigs;
	
	public Cohere(String link, String addr) throws ClientProtocolException, IOException, InterruptedException {
		if (!Coherence.instance.postCohered) {
			url = link;
			address = addr;
			
			cohereDir = new File("coherence", address);
			modlist = getModList();
			neededmods = getNeededModsList();
			downloadNeededMods();
			getConfigs();
			moveMods();
			writeConfigFile();
			MCRelauncher.restartMinecraft();
		}
	}
	
	private List<String> getModList() throws ClientProtocolException, IOException {
		logger.info("Getting mod list");
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		POSTGetter.get(url + "/modlist", stream);
		Type listType = new TypeToken<List<String>>() {}.getType();
		List<String> modlist = new Gson().fromJson(stream.toString(), listType);
		return modlist;
	}
	
	private List<String> getNeededModsList() {
		File modDir = new File(cohereDir, "mods");
		
		StringBuilder logList = new StringBuilder();
		logList.append("Needed mods: ");
		
		if ((!modDir.isDirectory()) || modDir.list().length == 0) { //If folder doesn't exist or is empty, return entire mod list
			modDir.mkdirs();
			for (String mod : modlist) {
				logList.append(mod);
				logList.append(", ");
			}
			logger.info(logList.toString());
			updateConfigs = true;
			return modlist;
		}
			
		List<String> currentMods = Arrays.asList(modDir.list());
		List<String> neededMods = new ArrayList<String>();
		for (String mod : modlist) { //Get list of mods that need to be downloaded from the server
			if (!currentMods.contains(mod))
				neededMods.add(mod);
		}
		
		for (String mod : currentMods) { //Delete all mods on client that are not on the server
			if (!modlist.contains(mod)) {
				logger.info("Deleting " + mod + " from local storage");
				new File(modDir, mod).delete();
			}
		}
		
		for (String mod : neededMods) { //List all needed mods
			logList.append(mod);
			logList.append(", ");
		}
		logger.info(logList.toString());
		
		if (!neededmods.isEmpty()) {
			updateConfigs = Library.getYesNo("There are mods to be updated."
					+ "\nWould you like to update the configs too?");
		}
		
		return neededMods;
	}
	
	private void downloadNeededMods() throws ClientProtocolException, IOException {
		if (neededmods.isEmpty()) {
			logger.info("No new mods needed.");
			return;
		}
		
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		HashMap<String, String> map = new HashMap<String, String>();
		final File modDir = new File(cohereDir, "mods");
		if (!modDir.isDirectory())
			modDir.mkdirs();
		
		int size = neededmods.size();
		for (int i = 0; i < size; i++) {
			stream.reset();
			String mod = neededmods.get(i);
			logger.info("Downloading mod " + mod + " (" + (i + 1) + "/" + size + ")");
			map.clear(); map.put("mod", mod);
			POSTGetter.get(url + "/mod", map, stream);
			
			File modFile = new File(modDir, neededmods.get(i));
			modFile.mkdirs();
			FileOutputStream fstream = new FileOutputStream(modFile);
			fstream.write(stream.toByteArray());
			fstream.close();
		}
	}
	
	private void getConfigs() throws IOException {
		File configZip;
		File customConfig = new File(cohereDir, "customConfig.zip");
		if (updateConfigs || !customConfig.exists()) { //Download the new config if updateConfigs is true
			logger.info("Downloading configs");
			try {
				FileUtils.moveDirectory(new File("config"), new File("oldConfig")); //Move config folder
			}
			catch (FileExistsException e) {}
			
			configZip = new File(cohereDir, "config.zip");
			if (configZip.exists())
				configZip.delete();
			configZip.createNewFile();
			
			FileOutputStream fstream = new FileOutputStream(configZip);
			ByteArrayOutputStream stream = new ByteArrayOutputStream();
			POSTGetter.get(url + "/config", stream);
			stream.writeTo(fstream);
			fstream.close();
			stream.close();
		}
		else { //Otherwise, use the old configs.
			configZip = customConfig;
		}
		
		logger.info("Extracting configs");
		new UnzipUtility().unzip(configZip.getPath(), new File("config").getPath());
		
		FileUtils.deleteQuietly(Coherence.instance.configFile); //Make sure that Coherence config carries over
		FileUtils.copyFile(new File("oldConfig", Coherence.instance.configFile.getName()), Coherence.instance.configFile);
	}
	
	private void moveMods() throws IOException {
		File modDir = new File("mods"); File curMods = new File("coherence", "localhost");
		FileUtils.copyDirectory(modDir, curMods);
		File cohereMods = new File(cohereDir, "mods");
		FileUtils.copyDirectory(cohereMods, modDir);
	}
	
	private void writeConfigFile() {
		logger.info("Writing configuration file for persistence");
		Configuration config = new Configuration(Coherence.instance.configFile);

		config.load();

		Property addressProperty = config.get(config.CATEGORY_GENERAL, "connectToServer", "null");
		addressProperty.set(address);
			
		config.save();
	}
}
