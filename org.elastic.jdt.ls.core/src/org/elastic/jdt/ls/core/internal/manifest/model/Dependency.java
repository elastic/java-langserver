package org.elastic.jdt.ls.core.internal.manifest.model;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class Dependency {
	
	private String groupId;

    private String artifactId;

    private String version;

    private String path;

    public Dependency(String groupId, String artifactId, String version) {
        this.setGroupId(groupId);
        this.setArtifactId(artifactId);
        this.setVersion(version);
    }

    public Dependency(String path) {
        this.setPath(path);
    }

    public void setPath(String path) {
        this.path = path;
    }
    
    public String getPath() {
    	return path;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(groupId)
                .append(artifactId)
                .append(version)
                .append(path)
                .toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("groupId", groupId)
                .append("artifactId", artifactId)
                .append("version", version)
                .append("path", path)
                .toString();
    }

}
