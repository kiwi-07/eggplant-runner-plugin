package io.jenkins.plugins.eggplant.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import hudson.FilePath;
import io.jenkins.plugins.eggplant.common.OperatingSystem;
import io.jenkins.plugins.eggplant.exception.BuilderException;
import io.jenkins.plugins.eggplant.exception.InvalidRunnerException;

public class CLIHelper{

  private final static String CLI_VERSION = "6.2.1-2";
  private final static Map<OperatingSystem, String> CLI_FILENAME = Stream.of(
      new AbstractMap.SimpleEntry<>(OperatingSystem.LINUX, "eggplant-runner-Linux-${cliVersion}"),
      new AbstractMap.SimpleEntry<>(OperatingSystem.MACOS, "eggplant-runner-MacOS-${cliVersion}"), 
      new AbstractMap.SimpleEntry<>(OperatingSystem.WINDOWS, "eggplant-runner-Windows-${cliVersion}.exe")
  ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  private final static String CLI_DOWNLOAD_URL = "https://downloads.eggplantsoftware.com/downloads/EggplantRunner/${cliFilename}";
  private final static String CLI_ENG_DOWNLOAD_URL = "https://gitlab.com/api/v4/projects/22402994/packages/generic/${cliVersion}/0.0.0/${cliFilename}";

  private PrintStream logger;
  private FilePath workspace;
  private String cliFilename;
  private FilePath cliFilePath;
  
  public CLIHelper(FilePath workspace, OperatingSystem os, PrintStream logger){
    this.workspace = workspace;
    this.cliFilename = CLI_FILENAME.get(os).replace("${cliVersion}", CLI_VERSION.toLowerCase()); // CLI filename must be lowercase
    this.cliFilePath = workspace.child("downloads").child(cliFilename);
    this.logger = logger;
  }

  public FilePath getFilePath(){
    return cliFilePath;
  }

  public String getFilename(){
    return cliFilename;
  }

  public void copyRunnerFrom(String path) throws IOException, InterruptedException
  {
    logger.println(">> Checking runner...");

    File file = new File(path);

    if(file.canRead() == false)
    {
      /* if the file does not exist, read access would be denied because the Java virtual machine has 
      insufficient privileges, or access cannot be determined */
      throw new InvalidRunnerException("No such file or permission denied", path);
    }
    
    FilePath filePath = new FilePath(file);
    
    if(filePath.getName().equals(cliFilename))
    {
        logger.println("Fetching runner from " + path);
        cliFilePath.copyFrom(filePath);
        logger.println("Fetch complete.");
    }
    else
    {
        throw new InvalidRunnerException("Cannot find '" + cliFilename + "'", path);
    }
  }

public void downloadRunner(String gitlabAccessToken) throws IOException, InterruptedException{
  String cliDownloadUrl = CLI_DOWNLOAD_URL.replace("${cliFilename}", cliFilename);
  Map<String,String> properties = new HashMap<>();

  logger.println(">> Downloading runner...");

  if(gitlabAccessToken == null){

    FilePath defaultCache = workspace.child("downloads").child(cliFilename);

    if(defaultCache.exists())
    {
      logger.println("Runner found in default directory, skipping download.");
    }
    else
    {
      downloadFromUrl(cliDownloadUrl, properties);
    }

  }
  else{
    cliDownloadUrl = CLI_ENG_DOWNLOAD_URL.replace("${cliVersion}", CLI_VERSION).replace("${cliFilename}", cliFilename);
    properties.put("PRIVATE-TOKEN", gitlabAccessToken);
    cliFilePath = workspace.child("downloads").child("eng").child(cliFilename);
    downloadFromUrl(cliDownloadUrl,properties);
  }

}

private FilePath downloadFromUrl(String url, Map<String, String> properties) throws BuilderException {

  try{

    logger.println("GET " + url);
    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
    for (String key : properties.keySet()) {
      connection.addRequestProperty (key,properties.get(key));
    }

    connection.setDoOutput(true);
    InputStream in = connection.getInputStream();
    cliFilePath.copyFrom(in);
    logger.println("Download successfully.");

  }catch(Exception e){
    throw new BuilderException("Download failed. Unable to download from url: "+ url +". Error details:" + e.toString());
  }

  return cliFilePath;
}

}
