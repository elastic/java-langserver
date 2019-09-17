package org.elastic.jdt.ls.core.internal;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.ls.core.internal.Environment;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.condpermadmin.ConditionalPermissionAdmin;
import org.osgi.service.condpermadmin.ConditionalPermissionInfo;
import org.osgi.service.condpermadmin.ConditionalPermissionUpdate;

public final class SecurityPermissionHelper {
	
	private static final String CLIENT_PORT = "CLIENT_PORT";
	private static final String CLIENT_HOST = "CLIENT_HOST";
	// Extra white host for repository, separated by commas
	private static final String EXTRA_WHITELIST_HOST = "EXTRA_WHITELIST_HOST";
	
	// Default repository white list
	private static final List<String> DEFAULT_HOST_WHITELIST = Arrays.asList(
			"repo.maven.apache.org",
			"oss.sonatype.org",
			"repository.jboss.org",
			"maven.google.com",
			"repo.clojars.org",
			"repo.eclipse.org");
	
	// Give other bundles all permissions except Maven and Gradle bundles
	private static final String CORE_PERM = 
		"ALLOW {" +
		   "[ org.osgi.service.condpermadmin.BundleLocationCondition  \"initial@reference:file:plugins/org.eclipse.buildship*\" \"!\" ]" +
		   "[ org.osgi.service.condpermadmin.BundleLocationCondition  \"initial@reference:file:plugins/org.eclipse.m2e*\" \"!\" ]" +
		   "( java.security.AllPermission \"*\" \"*\" )" +
		   "} \"Eclipse core bundles\"";
	
	// Deny all bundles to execute files
	private static final String DENY_EXEC_PERM = 
 	       "DENY {" +
 		   "( java.io.FilePermission \"<<ALL FILES>>\" \"execute\" )" +
 		   "} \"Deny permission\"";
	
	// permissions for Maven and Gradle bundles
	private static final String MINIMAL_PERM_TEMPLATE = 
 	        "ALLOW {" +
			"( org.osgi.framework.AdminPermission \"*\" \"*\" )" + 	
			"( org.osgi.framework.AdaptPermission \"*\" \"adapt\" )" +
			"( org.osgi.framework.PackagePermission \"*\" \"exportonly,import\" )" + 
			"( org.osgi.framework.ServicePermission \"*\" \"get,register\")" + 
			"( org.osgi.framework.BundlePermission \"*\" \"host,provide,fragment\")" +
			"( org.osgi.service.application.ApplicationAdminPermission \"*\" \"lifecycle, lock, schedule\" )" +
			"( org.eclipse.equinox.log.LogPermission \"*\" \"log\" )" +
			"( java.net.SocketPermission \"%s\" \"connect,resolve\" )" +
			"( java.net.SocketPermission \"localhost\" \"resolve\" )" +
			"( java.net.NetPermission \"getNetworkInformation\" )" +  
			"( java.lang.reflect.ReflectPermission \"suppressAccessChecks\" )" +
			"( java.io.FilePermission \"<<ALL FILES>>\" \"read\" )" + 	
			"( java.io.FilePermission \"%s\" \"write,delete\" )" + 
			"( java.io.SerializablePermission \"enableSubstitution\" )" +
			"( java.lang.RuntimePermission \"*\" \"*\" )" + 
			"( java.util.PropertyPermission \"*\" \"read, write\" )" +
			"( java.net.NetPermission \"getProxySelector\" )" +
			"%s } \"Normal bundles\"";
	
	private SecurityPermissionHelper() {}
	
	public static void setSecurityPermissions(BundleContext bundleContext) {
		
                ServiceReference sRef = bundleContext.getServiceReference(ConditionalPermissionAdmin.class.getName());
                
                // Get hold of OSGi ConditionalPermissionAdmin service...
                ConditionalPermissionAdmin conPermAdmin = (ConditionalPermissionAdmin) bundleContext.getService(sRef);
		
                // Create new "atomic rules update" object
                ConditionalPermissionUpdate update = conPermAdmin.newConditionalPermissionUpdate();

                // Get list of existing permissions (normally null)
                List<ConditionalPermissionInfo> perms = update.getConditionalPermissionInfos();
                
                // Clear old permissions
                update.getConditionalPermissionInfos().clear();
                
                ConditionalPermissionInfo coreP = conPermAdmin.newConditionalPermissionInfo(CORE_PERM);
                perms.add(coreP);
                ConditionalPermissionInfo denyP = conPermAdmin.newConditionalPermissionInfo(DENY_EXEC_PERM);
                perms.add(denyP);
                String minimalPerm = String.format(
                                MINIMAL_PERM_TEMPLATE,
                                Environment.get(CLIENT_HOST) + ":" + Environment.get(CLIENT_PORT),
                                getDataFolder(),
                                getSocketPermissions());
                ConditionalPermissionInfo minimalPermission = conPermAdmin.newConditionalPermissionInfo(minimalPerm);
                perms.add(minimalPermission);
	       
                update.commit();
	}
	
	private static String getDataFolder() {
		return Platform.getLocation().toFile().getParent() + File.pathSeparator + "-";
	}
	
	private static String getSocketPermissions() {
		String extraHosts = Environment.getEnvironment(EXTRA_WHITELIST_HOST);
		List<String> hostsWhiteList = DEFAULT_HOST_WHITELIST;
		if (extraHosts != null) {
			hostsWhiteList.addAll(Arrays.asList(extraHosts.split("\\s*,\\s*")));
		}
		return String.join(" ", hostsWhiteList.stream().map(host -> String.format("( java.net.SocketPermission \"%s\" \"connect,accept,resolve\" )", host)).collect(Collectors.toList()));
	}
	

}
