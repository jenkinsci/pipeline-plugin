package org.jenkinsci.plugins.workflow.cps.Snippetizer


def l = namespace(lib.LayoutTagLib)
def st = namespace("jelly:stapler")

st.contentType(value: "text/html;charset=UTF-8")

l.layout(title:_("Jenkins Workflow Reference"),permission:app.READ) {

    st.include(page: "sidepanel", it: app)
    l.main_panel {
        p {
            raw("Click ")
            a(href: "${app.rootUrl}${my.GDSL_URL}", target: "_blank") {
                raw("here for IntelliJ GDSL")
            }
            raw(".")

        }

        p {
            raw("Click ")
            a(href: "${app.rootUrl}${my.DSLD_URL}", target: "_blank") {
                raw("here for Eclipse DSLD")
            }
            raw(".")

        }
        st.include(page: "dslReferenceContent", it: my)
    }
}

