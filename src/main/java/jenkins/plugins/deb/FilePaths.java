package jenkins.plugins.deb;

import hudson.FilePath;

import java.io.IOException;


public class FilePaths {
    private FilePath workspace;

    FilePaths(FilePath workspace) throws IOException, InterruptedException {
        this.workspace = workspace;
        mkdirs();
    }

    public void mkdirs() throws IOException, InterruptedException {
        root().mkdirs();
        meta().mkdirs();
        aptCache().mkdirs();
        aptETC().mkdirs();
        aptState().mkdirs();
    }

    public FilePath root() {
        return workspace.child("debscm");
    }

    public FilePath meta() {
        return workspace.child("debscm/.meta");
    }

    public FilePath aptCache() {
        return workspace.child("debscm/.apt/cache");
    }

    public FilePath aptETC() {
        return workspace.child("debscm/.apt/etc");
    }

    public FilePath aptState() {
        return workspace.child("debscm/.apt/state");
    }

    public String[] standardAPTOptions() throws IOException, InterruptedException {
        // TODO maybe move the path crap to a stateful helper class
        mkdirs();
        String[] opts =  {
                "-o", "Dir::Cache=" + aptCache().getRemote(),
                "-o", "Dir::Etc=" + aptETC().getRemote(),
                "-o", "Dir::State=" + aptState().getRemote()
        };
        return opts;
    }
}
