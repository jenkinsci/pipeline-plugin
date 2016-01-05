package org.jenkinsci.plugins.workflow.cps.Snippetizer


def st = namespace("jelly:stapler")

p {
    a(href: "${rootUrl}${my.DSL_HELP_URL}", target: "_blank") {
        _("Click here for this doc in a new window.")
    }

}

p {
    a(href: "${rootUrl}${my.GDSL_URL}", target: "_blank") {
        _("Click here for IntelliJ GDSL.")
    }

}

p {
    a(href: "${rootUrl}${my.DSLD_URL}", target: "_blank") {
        _("Click here for Eclipse DSLD.")
    }

}

st.include(page: "dslReferenceContent")

