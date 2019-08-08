package org.elastic.jdt.ls.core.internal.manifest;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;

import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;

import org.elastic.jdt.ls.core.internal.ElasticJavaLanguageServerPlugin;
import org.elastic.jdt.ls.core.internal.manifest.model.Repo;


public class ArtifactResolver {

    public final static String MAVEN_LOCAL = System.getProperty("JAVA_LANGSERVER_CACHE") != null ? Paths.get(System.getProperty("JAVA_LANGSERVER_CACHE"), ".manifest").toString() :
		Paths.get(ResourcesPlugin.getWorkspace().getRoot().getLocation().toString(), ".manifest").toString();
	
	public static RepositorySystem newRepositorySystem() {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
 
        locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
            @Override
            public void serviceCreationFailed( Class<?> type, Class<?> impl, Throwable exception )
            {
            	ElasticJavaLanguageServerPlugin.logException("Service creation failed for" + type + "implementation" + impl + ": " + exception.getMessage(), exception);
            }
        });

        return locator.getService(RepositorySystem.class);
	}
	
	
	public static DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

        LocalRepository localRepo = new LocalRepository(MAVEN_LOCAL);
        // set request timeout to 10 min
        session.setConfigProperty(ConfigurationProperties.REQUEST_TIMEOUT, 10 * 60 * 1000);
        // set connect timeout to 1 min
        session.setConfigProperty(ConfigurationProperties.CONNECT_TIMEOUT, 60 * 1000);
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));

//        session.setTransferListener(new ConsoleTransferListener());
//        session.setRepositoryListener(new ConsoleRepositoryListener());
        
        return session;
    }

    public static List<RemoteRepository> newRepositories(RepositorySystem system, RepositorySystemSession session, List<Repo> repoConfigs) {
    	if (repoConfigs.size() == 0) {
    		// By default we use maven central
    		return new ArrayList<>(Collections.singletonList(newCentralRepository()));
    	} else {
    		ArrayList<RemoteRepository> repos = new ArrayList();
    		repoConfigs.forEach(r -> repos.add(newRepositoryFromConfig(r)));
    		return repos;
    	}
    }
    
    private static RemoteRepository newRepositoryFromConfig(Repo repoConfig) {
    	return new RemoteRepository.Builder("central", "default", repoConfig.getUrl()).build();
    }
  

    private static RemoteRepository newCentralRepository()
    {
        return new RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2/").build();
    }
	
	

}
