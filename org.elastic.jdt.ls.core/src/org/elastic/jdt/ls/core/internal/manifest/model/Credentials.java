package org.elastic.jdt.ls.core.internal.manifest.model;

public class Credentials {
	
	private String username;

    private String password;

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public String toString() {
        return "username: " + this.username + ", password: " + this.password;
    }

}
