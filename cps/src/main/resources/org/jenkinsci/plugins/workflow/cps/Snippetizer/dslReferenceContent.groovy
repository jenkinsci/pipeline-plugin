package org.jenkinsci.plugins.workflow.cps.Snippetizer

import org.jenkinsci.plugins.workflow.cps.GlobalVariable
import org.jenkinsci.plugins.workflow.cps.Snippetizer
import org.jenkinsci.plugins.workflow.steps.StepDescriptor
import org.jenkinsci.plugins.workflow.structs.DescribableHelper

import javax.servlet.RequestDispatcher

Snippetizer snippetizer = my;

def l = namespace(lib.LayoutTagLib)
def st = namespace("jelly:stapler")

st.adjunct(includes: 'org.jenkinsci.plugins.workflow.cps.Snippetizer.css.workflow')

h1(_("DSL Reference"))
div(class:'steps-box basic'){
	h2(_("Steps"))
	dl(class:'steps basic root'){
		for (StepDescriptor d : snippetizer.getStepDescriptors(false)) {
		    generateStepHelp(d);
		}
	}
}

div(class:'steps-box advanced'){
	h2(_("Advanced/Deprecated Steps"))
	dl(class:'steps advanced root'){
		for (StepDescriptor d : snippetizer.getStepDescriptors(true)) {
		    generateStepHelp(d);
		}
	}
}

div(class:'steps-box variables'){

	h2(_("Variables"))
	dl(class:'steps variables root'){
		for (GlobalVariable v : snippetizer.getGlobalVariables()) {
		    dt {
		        code(v.getName())
		    }
		    RequestDispatcher rd = request.getView(v, "help");
		    dd{
			    div(class:"help", style:"display: block") {
			        if (rd != null) {
			            st.include(page: "help", it: v)
			        } else {
			            raw("(no help)")
			        }
			    }
			}
		}
	}
}

def generateStepHelp(StepDescriptor d) throws Exception {
    return {
    	dt(class:'step-title'){
            code(d.getFunctionName())
            raw(": ${d.getDisplayName()}") 
    	}
    	dd(class:'step-body'){
	        try {
	            generateHelp(DescribableHelper.schemaFor(d.clazz), 3);
	        } catch (Exception x) {
	            pre {
	                code(x)
	            }
	        }
	    }
    }.call()
}

def generateHelp(DescribableHelper.Schema schema, int headerLevel) throws Exception {
    return {
        String help = schema.getHelp(null);
        if (help != null && !help.equals("")) {
            div(class:"help", style:"display: block") {
                raw(help)
            }
        }
        dl(class:'help-list mandatory'){
        // TODO else could use RequestDispatcher (as in Descriptor.doHelp) to serve template-based help
	        for (String attr : schema.mandatoryParameters()) {
	        	dt(class:'help-title'){
	                code(attr)    
	        	}
	        	dd(class:'help-body'){
	            	generateAttrHelp(schema, attr, headerLevel);
	        	}
	        }
    	}
    	dl(class:'help-list optional'){
	        for (String attr : schema.parameters().keySet()) {
	            if (schema.mandatoryParameters().contains(attr)) {
	                continue;
	            }
	            dt(class:'help-title'){
		            code(attr)
		            raw(" (optional)")
		            
	        	}
	        	dd(class:'help-body'){
	            	generateAttrHelp(schema, attr, headerLevel);
	        	}
	        }
    	}
    }.call()
}

def generateAttrHelp(DescribableHelper.Schema schema, String attr, int headerLevel) throws Exception {
    return {
        String help = schema.getHelp(attr);
        if (help != null && !help.equals("")) {
            div(class:"help", style:"display: block") {
                raw(help)
            }
        }
        DescribableHelper.ParameterType type = schema.parameters().get(attr);
        describeType(type, headerLevel);
    }.call()
}

def describeType(DescribableHelper.ParameterType type, int headerLevel) throws Exception {
    return {
        int nextHeaderLevel = Math.min(6, headerLevel + 1);
        if (type instanceof DescribableHelper.AtomicType) {
            div {
                strong(_("Type:"))
                text(type)
            }
        } else if (type instanceof DescribableHelper.EnumType) {
        	div(class:'values-box nested'){
                div(class:'marker-title value-title'){
                	span(_("Values:"))	
                }
                for (String v : ((DescribableHelper.EnumType) type).getValues()) {
                    div(class:'value list-item') {
                        code(v)
                    }
                }
        	}
        } else if (type instanceof DescribableHelper.ArrayType) {
        	div(class:'array-list-box marker'){
	            div(class:'array-title marker-title'){
	            	span(_("Array/List:"))
	           	}
	            div(class:'array-list'){
	            	describeType(((DescribableHelper.ArrayType) type).getElementType(), headerLevel)
        		}
        	}
        } else if (type instanceof DescribableHelper.HomogeneousObjectType) {
            dl(class:'nested-object-box nested') {
                dt(_("Nested object"))
            	dd{
            		generateHelp(((DescribableHelper.HomogeneousObjectType) type).getSchemaType(), nextHeaderLevel);
        		}
        	}
        } else if (type instanceof DescribableHelper.HeterogeneousObjectType) {
            dl(class:'nested-choice-box nested') {
                dt(_("Nested choice of objects"))
            	dd{

		            dl(class:'schema') {
		                for (Map.Entry<String, DescribableHelper.Schema> entry : ((DescribableHelper.HeterogeneousObjectType) type).getTypes().entrySet()) {
		                    dt {
		                        code(DescribableHelper.CLAZZ + ": '" + entry.getKey() + "'")
		                    }
		                    dd{
		                    	generateHelp(entry.getValue(), nextHeaderLevel);
		                	}
		                }
		            }
		        }
        	}
        } else if (type instanceof DescribableHelper.ErrorType) {
            Exception x = ((DescribableHelper.ErrorType) type).getError();
            pre {
                code(x)
            }
        } else {
            assert false: type;
        }
    }.call()
}
st.adjunct(includes: 'org.jenkinsci.plugins.workflow.cps.Snippetizer.js.workflow')