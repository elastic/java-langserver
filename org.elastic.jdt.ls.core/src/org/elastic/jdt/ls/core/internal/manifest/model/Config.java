package org.elastic.jdt.ls.core.internal.manifest.model;

import java.util.List;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class Config {
	private List<ProjectInfo> projectInfos;

    public Config(List<ProjectInfo> projectInfos) {
        this.setProjectInfos(projectInfos);
    }

    public void setProjectInfos(List<ProjectInfo> projectInfos){
        this.projectInfos = projectInfos;
    }

    public List<ProjectInfo> getProjectInfos() {
        return this.projectInfos;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("config", projectInfos)
                .toString();
    }

}
