package org.elastic.jdt.ls.core.internal;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.SystemUtils;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.ls.core.internal.Environment;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.condpermadmin.ConditionalPermissionAdmin;
import org.osgi.service.condpermadmin.ConditionalPermissionInfo;
import org.osgi.service.condpermadmin.ConditionalPermissionUpdate;

@SuppressWarnings("restriction")
public final class SecurityPermissionHelper {
	
	private static final String CLIENT_PORT = "CLIENT_PORT";
	private static final String CLIENT_HOST = "CLIENT_HOST";
	// Extra white host for repository, separated by commas
	private static final String EXTRA_WHITELIST_HOST = "EXTRA_WHITELIST_HOST";
	
	// Default repository white list
	private static final List<String> DEFAULT_HOST_WHITELIST = Collections.unmodifiableList(Arrays.asList(
                "repo.maven.apache.org",
                "oss.sonatype.org",
                "repository.jboss.org",
                "maven.google.com",
                "repo.clojars.org",
                "repo.eclipse.org"
        ));
	
	// Give other bundles all permissions except Maven and Gradle bundles
	private static final String CORE_PERM = 
		"ALLOW {" +
		   "[ org.osgi.service.condpermadmin.BundleLocationCondition  \"initial@reference:file:plugins/org.eclipse.buildship*\" \"!\" ]" +
		   "[ org.osgi.service.condpermadmin.BundleLocationCondition  \"initial@reference:file:plugins/org.eclipse.m2e*\" \"!\" ]" +
		   "( java.security.AllPermission \"*\" \"*\" )" +
		   "} \"Eclipse core bundles\"";
	
	// Deny all bundles to execute files
	private static final String DENY_PERM_FORALL = 
		"DENY {" +
		   "( java.lang.RuntimePermission \"accessClassInPackage.sun\" )" +
		   "( java.lang.RuntimePermission \"setSecurityManager\" )" +
		   "( java.security.Permission \"setProperty.package.access\" )" +
 		   "} \"Deny permission for all bundles\"";
	
	private static final String NON_M2E_DENY_PERM =
		"DENY {" +
		   "[ org.osgi.service.condpermadmin.BundleLocationCondition  \"initial@reference:file:plugins/org.eclipse.m2e*\" \"!\" ]" +
 		   "( java.lang.reflect.ReflectPermission \"suppressAccessChecks\" )" +
		   "( java.lang.RuntimePermission \"createClassLoader\" )" +
		   "( java.io.FilePermission \"<<ALL FILES>>\" \"execute\" )" +
 		   "} \"Non-m2e deny permission\"";
	
	private static final String NON_GRADLE_DENY_PERM =
		"DENY {" +
		   "[ org.osgi.service.condpermadmin.BundleLocationCondition  \"initial@reference:file:plugins/org.eclipse.buildship*\" \"!\" ]" +
		   "( java.io.FilePermission \"<<ALL FILES>>\" \"execute\" )" +
 		   "} \"Non-gradle deny permission\"";
	
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
			"( java.io.FilePermission \"/usr/libexec/java_home\" \"execute\" )" +
			"( java.io.SerializablePermission \"enableSubstitution\" )" +
			"( java.lang.RuntimePermission \"*\" \"*\" )" + 
			"( java.util.PropertyPermission \"*\" \"read, write\" )" +
			"( java.net.NetPermission \"getProxySelector\" )" +
			"%s } \"Minimal permissions\"";
	
	// Gradle integration specific permissions
	public static final String GRADLE_PERM_TEMPLATE = 
		"ALLOW {" +
			"( java.lang.management.ManagementPermission \"monitor\" )" +
			"( java.net.SocketPermission \"localhost:0\" \"listen,resolve\" )" +
			"( java.net.SocketPermission \"services.gradle.org\" \"resolve,accept,connect\" )" +
			"%s } \"Buildship permission template\"";
	
	private SecurityPermissionHelper() {}
	
	public static void setSecurityPermissions(BundleContext bundleContext) {
		
		ServiceReference<?> sRef = bundleContext.getServiceReference(ConditionalPermissionAdmin.class.getName());
		
		// Get hold of OSGi ConditionalPermissionAdmin service...
		ConditionalPermissionAdmin conPermAdmin = (ConditionalPermissionAdmin) bundleContext.getService(sRef);

		// Create new "atomic rules update" object
		ConditionalPermissionUpdate update = conPermAdmin.newConditionalPermissionUpdate();

		// Get list of existing permissions (normally null)
		List<ConditionalPermissionInfo> perms = update.getConditionalPermissionInfos();
		
		// Clear old permissions
		perms.clear();
		
		ConditionalPermissionInfo coreP = conPermAdmin.newConditionalPermissionInfo(CORE_PERM);
		perms.add(coreP);
		ConditionalPermissionInfo denyP = conPermAdmin.newConditionalPermissionInfo(DENY_PERM_FORALL);
		perms.add(denyP);
		ConditionalPermissionInfo nonM2eDenyP = conPermAdmin.newConditionalPermissionInfo(NON_M2E_DENY_PERM);
		perms.add(nonM2eDenyP);
		ConditionalPermissionInfo nonGradleDenyP = conPermAdmin.newConditionalPermissionInfo(NON_GRADLE_DENY_PERM);
		perms.add(nonGradleDenyP);
		String minimalPerm = String.format(
				MINIMAL_PERM_TEMPLATE,
				Environment.get(CLIENT_HOST) + ":" + Environment.get(CLIENT_PORT),
				StringEscapeUtils.escapeJava(getDataFolder()),
				getSocketPermissions());
		ConditionalPermissionInfo minimalPermission = conPermAdmin.newConditionalPermissionInfo(minimalPerm);
		perms.add(minimalPermission);
	
		update.commit();
		ElasticJavaLanguageServerPlugin.logInfo("Succeeded to set security permissions with:\n" +
				coreP.toString() + "\n" +
				denyP.toString() + "\n" +
				nonM2eDenyP.toString() + "\n" +
				nonGradleDenyP.toString() + "\n" +
				minimalPermission.toString());
	}
	
	public static void updateGradlePermissions(BundleContext bundleContext) {
		ServiceReference<?> sRef = bundleContext.getServiceReference(ConditionalPermissionAdmin.class.getName());
		ConditionalPermissionAdmin conPermAdmin = (ConditionalPermissionAdmin) bundleContext.getService(sRef);
		ConditionalPermissionUpdate update = conPermAdmin.newConditionalPermissionUpdate();
		List<ConditionalPermissionInfo> perms = update.getConditionalPermissionInfos();
		String gradlePerm = String.format(GRADLE_PERM_TEMPLATE, "");;
		String javaPath = getJavaPath();
		if (javaPath != null) {
			gradlePerm = String.format(GRADLE_PERM_TEMPLATE, "( java.io.FilePermission \"" + StringEscapeUtils.escapeJava(javaPath) + "\" \"execute\" )");
		}
		ConditionalPermissionInfo gradleP = conPermAdmin.newConditionalPermissionInfo(gradlePerm);
		perms.add(gradleP);
		update.commit();
		ElasticJavaLanguageServerPlugin.logInfo("Succeeded to update security permissions with:\n" + gradleP.toString());
	}
	
	private static String getJavaPath() {
		Path javaHomePath = Paths.get(System.getProperty("java.home"));
		Path javaPath = SystemUtils.IS_OS_WINDOWS ? Paths.get(javaHomePath.toString(), "bin", "java.exe") : Paths.get(javaHomePath.toString(), "bin", "java");
		if (javaPath.toFile().exists()) {
			return javaPath.toString();
		}
		return null;
	}
	
	private static String getDataFolder() {
		return Platform.getLocation().toFile().getParent() + File.separator + "-";
	}
	
	private static String getSocketPermissions() {
		String extraHosts = Environment.getEnvironment(EXTRA_WHITELIST_HOST);
		List<String> hostsWhiteList = new ArrayList<String>(DEFAULT_HOST_WHITELIST);
		if (extraHosts != null) {
			hostsWhiteList.addAll(Arrays.asList(extraHosts.split("\\s*,\\s*")));
		}
		return String.join(" ", hostsWhiteList.stream().map(host -> String.format("( java.net.SocketPermission \"%s\" \"connect,accept,resolve\" )", StringEscapeUtils.escapeJava(host))).collect(Collectors.toList()));
	}
	

}
