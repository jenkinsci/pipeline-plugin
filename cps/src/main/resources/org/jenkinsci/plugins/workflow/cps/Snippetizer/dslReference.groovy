package org.jenkinsci.plugins.workflow.cps.Snippetizer

import org.jenkinsci.plugins.workflow.cps.Snippetizer


def l = namespace(lib.LayoutTagLib)
def st = namespace("jelly:stapler")

l.layout(title:_("Jenkins Workflow Reference")) {

    st.include(page: "sidepanel", it: app)
    l.main_panel {
        p {
            a(href: "${rootURL}/${Snippetizer.GDSL_URL}", target: "_blank") {
                raw(_("Click here for IntelliJ GDSL."))
            }

        }

        /* Commenting out DSLD until it's fixed.
        p {
            a(href: "${rootURL}/${Snippetizer.DSLD_URL}", target: "_blank") {
                raw(_("Click here for Eclipse DSLD."))
            }

        }
        */
        st.include(page: "dslReferenceContent")
    }
}

