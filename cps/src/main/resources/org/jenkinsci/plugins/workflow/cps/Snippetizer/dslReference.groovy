package org.jenkinsci.plugins.workflow.cps.Snippetizer

import org.jenkinsci.plugins.workflow.cps.Snippetizer


def l = namespace(lib.LayoutTagLib)
def st = namespace("jelly:stapler")

l.layout(title:_("Jenkins Pipeline Reference")) {

    st.include(page: "sidepanel", it: app)
    l.main_panel {
        st.include(page: "dslReferenceContent")
    }
}

