package org.jenkinsci.plugins.workflow.cps.Snippetizer


def st = namespace("jelly:stapler")

p {
    raw("Click ")
    a(href: "${app.rootUrl}${my.DSL_HELP_URL}", target: "_blank") {
        raw("here")
    }
    raw(" for this doc in a new window.")

}

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

