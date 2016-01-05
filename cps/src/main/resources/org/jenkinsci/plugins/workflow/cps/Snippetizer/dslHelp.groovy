package org.jenkinsci.plugins.workflow.cps.Snippetizer


def l = namespace(lib.LayoutTagLib)
def st = namespace("jelly:stapler")

l.layout(title:_("Jenkins Workflow Reference")) {

    st.include(page: "sidepanel", it: app)
    l.main_panel {
        p {
            a(href: "${app.rootUrl}${my.GDSL_URL}", target: "_blank") {
                _("Click here for IntelliJ GDSL.")
            }

        }

        p {
            a(href: "${app.rootUrl}${my.DSLD_URL}", target: "_blank") {
                _("Click here for Eclipse DSLD.")
            }

        }
        st.include(page: "dslReferenceContent")
    }
}

