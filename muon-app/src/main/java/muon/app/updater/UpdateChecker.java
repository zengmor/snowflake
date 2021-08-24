package muon.app.updater;

import java.net.ProxySelector;
import java.net.URL;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.markusbernhardt.proxy.ProxySearch;

import lombok.extern.slf4j.Slf4j;
import muon.app.App;

@Slf4j
public class UpdateChecker {
	public static final String UPDATE_URL = "https://api.github.com/repos/subhra74/snowflake/releases/latest";

	private static final ObjectMapper objectMapper = new ObjectMapper();
	static {
		CertificateValidator.registerCertificateHook();
	}

	public static boolean isNewUpdateAvailable() {
		try {
			ProxySearch proxySearch = ProxySearch.getDefaultProxySearch();
			ProxySelector myProxySelector = proxySearch.getProxySelector();
			ProxySelector.setDefault(myProxySelector);

			//System.out.println("Checking for url");
			log.info("checking update for url");
			objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
			VersionEntry latestRelease = objectMapper.readValue(new URL(UPDATE_URL).openStream(),
					new TypeReference<VersionEntry>() {
					});
			//System.out.println("Latest release: " + latestRelease);
			log.info("Latest release: {}", latestRelease);
			return latestRelease.compareTo(App.VERSION) > 0;
		} catch (Exception e) {
			//e.printStackTrace();
			log.error("checking update available failed with ", e);
		}
		return false;
	}
}
