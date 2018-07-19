package jenkins.plugins.deb;

import hudson.scm.SCMRevisionState;

public class DebSCMRevisionState extends SCMRevisionState {

    public String getDebVersion() {
        return debVersion;
    }

    private String debVersion;

    public DebSCMRevisionState(String debVersion) {
        this.debVersion = debVersion;
    }

    @Override
    public String toString() {
        return "DebSCMRevisionState debVersion:" + debVersion;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof DebSCMRevisionState)) {
            return false;
        }
        return debVersion == ((DebSCMRevisionState)other).getDebVersion();
    }
}
