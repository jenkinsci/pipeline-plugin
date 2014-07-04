import hudson.model.*;
import jenkins.model.*;

try {
    println "Hello world"
    def j = Jenkins.instance;
    def p = j.pluginManager;
    def uc = j.updateCenter;

    uc.sites.remove(uc.getSite('default'))
    uc.sites.add(new UpdateSite('default','https://updates.jenkins-ci.org/experimental/update-center.json'));

    p.doCheckUpdatesServer();
    ['async-http-client','workflow-aggregator'].each { n ->
      j.updateCenter.getById('default').getPlugin(n).deploy(false).get();
    }

    System.exit(0)
} catch (Throwable t) {
    t.printStackTrace();
    System.exit(1);
}
