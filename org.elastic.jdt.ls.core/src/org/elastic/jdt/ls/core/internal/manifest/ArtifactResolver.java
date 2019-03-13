package org.elastic.jdt.ls.core.internal.manifest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;

import org.eclipse.aether.transport.wagon.WagonTransporterFactory;

import org.elastic.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.elastic.jdt.ls.core.internal.manifest.model.Repo;


public class ArtifactResolver {
	
	public static RepositorySystem newRepositorySystem() {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, WagonTransporterFactory.class);
 
        locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
            @Override
            public void serviceCreationFailed( Class<?> type, Class<?> impl, Throwable exception )
            {
            	JavaLanguageServerPlugin.logException("Service creation failed for" + type + "implementation" + impl + ": " + exception.getMessage(), exception);
            }
        });

        return locator.getService(RepositorySystem.class);
	}
	
	
	public static DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

        LocalRepository localRepo = new LocalRepository("target/local-repo");
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
    	return new RemoteRepository.Builder(null, repoConfig.getRepoType().toString(), repoConfig.getUrl()).build();
    }
  

    private static RemoteRepository newCentralRepository()
    {
        return new RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2/").build();
    }
	
	

}
