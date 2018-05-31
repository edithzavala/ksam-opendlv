package org.ksam.opendlv.adapter.server;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.ksam.opendlv.adapter.replay.Simulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;

import io.reactivex.Observable;

public class AdapterServer implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdapterServer.class);
    private final int PORT_OPENDLV = 8083;
    private final int PORT_KSAM = 8080;
    // Adapt for supporting different vehicles. E.g., get system ID from JSON
    // received.
    private final String SYSTEM_ID = "openDlvMonitorv0";
    // private final RestTemplate REST_TEMPLATE = new RestTemplate();
    private final String URL_KSAM = "http://localhost:" + PORT_KSAM;
    private final String CONFIG = "/json/" + SYSTEM_ID + ".json";

    private final boolean replay = true;

    public AdapterServer() {
	InputStream is = AdapterServer.class.getResourceAsStream(CONFIG);
	StringBuilder json = new StringBuilder();
	try (Reader reader = new BufferedReader(
		new InputStreamReader(is, Charset.forName(StandardCharsets.UTF_8.name())))) {
	    int c = 0;
	    while ((c = reader.read()) != -1) {
		json.append((char) c);
	    }
	} catch (IOException e) {
	    LOGGER.error(e.getMessage());
	}
	LOGGER.info("Susbcribe openDlvMonitor");
	postData(json.toString(), URL_KSAM + "/managedElement");
	if (!replay) {
	    (new Thread(this)).start();
	} else {
	    new Simulator(this);
	}
    }

    private void postData(String jsonData, String url) {
	HttpHeaders httpHeaders = new HttpHeaders();
	httpHeaders.set("Content-Type", "application/json");
	HttpEntity<String> httpEntity = new HttpEntity<String>(jsonData, httpHeaders);
	RestTemplate restTemplate = new RestTemplate();
	restTemplate.postForObject(url, httpEntity, String.class);
    }

    public void forwardData(String s) {
	String sJsonFormmat = s.replace('\'', '\"');
	LOGGER.info("Forward JSON: " + sJsonFormmat);
	postData(sJsonFormmat, URL_KSAM + "/" + SYSTEM_ID + "/monitoringData");
    }

    @Override
    public void run() {
	LOGGER.info("KSAM Adapter server up");
	ServerSocket server = null;
	try {
	    server = new ServerSocket(PORT_OPENDLV);
	} catch (IOException e1) {
	    LOGGER.error(e1.getMessage());
	}
	while (true) {
	    try {
		Socket socket = server.accept();

		DataInputStream dis = new DataInputStream(socket.getInputStream());
		byte[] data = new byte[2048];
		dis.read(data);
		String s = new String(data);
		// LOGGER.info(s);

		Observable.just(s).subscribe(t -> forwardData(t));

		dis.close();
		socket.close();
	    } catch (IOException e1) {
		LOGGER.error(e1.getMessage());
	    }
	}
    }

    public static void main(String args[]) throws IOException, ClassNotFoundException {
	new AdapterServer();
    }
}
