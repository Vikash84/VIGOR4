package com.vigor.RegressionTest;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.vigor.component.VirusGenome;
import com.vigor.utils.VigorTestUtils;
import com.vigor.utils.VigorUtils;

public class GenerateExonerateOutputTest {
	

	
	public String queryExonerate(VirusGenome virusGenome, String referenceDB,String workspace) {

		File file = new File(workspace + "/sequence_temp.fasta");
		Path path = Paths.get(file.getAbsolutePath());
		System.out.println("Length is" + virusGenome.getSequence().getLength());
		List<String> sequence = java.util.Arrays.asList(virusGenome.getSequence().toString().split("(?<=\\G.{70})"));
		try (BufferedWriter writer = Files.newBufferedWriter(path)) {
			writer.write(">" + virusGenome.getId() + " " + virusGenome.getDefline());
			sequence.stream().forEach(line -> {
				try {
					writer.write("\n");
					writer.write(line);
				} catch (IOException e) {
					System.out.println(e.getMessage());
				}
			});

		} catch (IOException e) {
			System.out.println(e.getMessage());
		}

		// connect to lserver1 and run exonerate
		String fileName="";
		fileName = virusGenome.getId().replaceAll("\\|", "")+".txt";
		String refDBFolder = referenceDB.replaceAll("_db", "");
		try {
			Runtime.getRuntime().exec(
					"pscp -pw \"Ilovemyself@3\" -scp "+file.getAbsolutePath()+" lserver1:/home/snettem/vigor4_workspace/");

			String dbPath = "/home/snettem/vigor4_workspace/data3/" + referenceDB;
			String host = "lserver1";
			String user = "snettem";
			String password = "Ilovemyself@3";
			String command1 = "chmod 777 /home/snettem/vigor4_workspace/sequence_temp.fasta && exonerate --model protein2genome -q "
					+ dbPath
					+ " -t /home/snettem/vigor4_workspace/sequence_temp.fasta --showcigar true > /home/snettem/vigor4_workspace/"+fileName +" && chmod 777 /home/snettem/vigor4_workspace/"+fileName;

			java.util.Properties config = new java.util.Properties();
			config.put("StrictHostKeyChecking", "no");
			JSch jsch = new JSch();
			Session session = jsch.getSession(user, host, 22);
			session.setPassword(password);
			session.setConfig(config);
			session.connect();
			System.out.println("Connected to unix server");

			Channel channel = session.openChannel("exec");
			((ChannelExec) channel).setCommand(command1);
			channel.setInputStream(null);
			((ChannelExec) channel).setErrStream(System.err);

			InputStream in = channel.getInputStream();
			channel.connect();
			byte[] tmp = new byte[1024];
			while (true) {
				while (in.available() > 0) {
					int i = in.read(tmp, 0, 1024);
					if (i < 0)
						break;
					System.out.print(new String(tmp, 0, i));
				}
				if (channel.isClosed()) {
					System.out.println("exit-status: " + channel.getExitStatus());
					break;
				}
				
			}
			channel.disconnect();
			session.disconnect();
			
					Runtime.getRuntime().exec(
					"pscp -pw \"Ilovemyself@3\" -scp lserver1:/home/snettem/vigor4_workspace/"+fileName+" "+workspace+"/"+refDBFolder+"/"+fileName);
					
			System.out.println("DONE");
			
			
		} catch (Exception e) {
			e.printStackTrace();
		}

		// *********************************
		 
		
		return workspace+"/"+refDBFolder+"/"+fileName;
	}
	
	
	
}
