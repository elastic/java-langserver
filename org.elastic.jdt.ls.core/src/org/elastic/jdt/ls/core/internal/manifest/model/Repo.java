package org.elastic.jdt.ls.core.internal.manifest.model;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class Repo {
	public enum RepoTypes {
        IVY, LOCAL, MAVEN;
    }

    private RepoTypes repoType;

    private String url;

    private Credentials credentials;

    public void setRepoType(RepoTypes repoType) {
        this.repoType = repoType;
    }

    public RepoTypes getRepoType() {
        return repoType;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    public void setCredentials(Credentials credentials) {
        this.credentials = credentials;
    }

    public Credentials getCredentials() {
        return credentials;
    }

    @Override
    public boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }

    @Override
    public String toString() {
    	return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("repoType", repoType)
                .append("url", url)
                .append("credentials", credentials)
                .toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(repoType)
                .append(url)
                .append(credentials)
                .toHashCode();
    }
}
