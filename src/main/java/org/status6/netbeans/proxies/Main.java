/*
 * Copyright 2022 John Neffenger.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.status6.netbeans.proxies;

import java.io.File;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileSet;
import org.netbeans.nbbuild.extlibs.DownloadBinaries;

/**
 * Runs the NetBeans Proxy Tests. Run this program after starting a proxy server
 * and defining the proxy environment variables. For example:
 * <pre>{@code
 * export http_proxy=http://10.10.10.1:3128/
 * export https_proxy=http://10.10.10.1:3128/
 * }</pre>
 */
public class Main {

    /**
     * Location for the {@code binaries.cache} property.
     */
    private static final String BINARIES_CACHE = "hgexternalcache";

    /**
     * Value of the {@code binaries.server} property in the NetBeans build.
     */
    private static final String BINARIES_SERVER = "https://netbeans.osuosl.org/binaries/";

    /**
     * Value of the {@code binaries.repos} property in the NetBeans build.
     */
    private static final String BINARIES_REPOS = "https://repo1.maven.org/maven2/";

    /**
     * Manifest files included for the {@code download-all-extbins} target.
     */
    private static final String MANIFEST_INCLUDE = "**/external/binaries-list";

    /**
     * Sole constructor.
     */
    public Main() {
    }

    /**
     * Executes the {@code DownloadBinaries} task.
     *
     * @param args the arguments on the command line
     */
    public static void main(String[] args) {
        var logger = new DefaultLogger();
        logger.setErrorPrintStream(System.err);
        logger.setOutputPrintStream(System.out);
        logger.setMessageOutputLevel(Project.MSG_DEBUG);

        var project = new Project();
        project.init();
        project.setName("NetBeans Proxy Tests");
        project.addBuildListener(logger);

        /*
         * See the "download-all-extbins" target in the NetBeans build file:
         * https://github.com/apache/netbeans/blob/master/nbbuild/build.xml#L235
         */
        var task = new DownloadBinaries();
        task.setProject(project);
        task.setTaskName("downloadbinaries");
        task.setCache(new File(BINARIES_CACHE));
        task.setServer(BINARIES_SERVER);
        task.setRepos(BINARIES_REPOS);
        var fileset = new FileSet();
        fileset.setDir(new File("."));
        fileset.setIncludes(MANIFEST_INCLUDE);
        task.addManifest(fileset);
        task.execute();
    }
}
