package hu.blackbelt.judo.tatami.psm2asm.zeta.rules;

/*-
 * #%L
 * JUDO Tatami parent
 * %%
 * Copyright (C) 2018 - 2024 BlackBelt Technology
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

import hu.blackbelt.judo.meta.psm.data.BoundOperation;
import hu.blackbelt.judo.meta.psm.data.EntityType;
import hu.blackbelt.judo.meta.psm.namespace.NamespaceElement;
import hu.blackbelt.judo.meta.psm.service.*;
import hu.blackbelt.judo.meta.psm.service.Parameter;
import hu.blackbelt.judo.zeta.annotation.*;
import hu.blackbelt.judo.zeta.transformation.core.TransformFunction;
import hu.blackbelt.judo.zeta.transformation.core.TransformationContext;
import org.eclipse.emf.ecore.*;

import static hu.blackbelt.judo.tatami.psm2asm.zeta.Psm2AsmHelper.*;

/**
 * Operation transformation rules from operation.etl.
 * <p>
 * Rules:
 * <ul>
 *   <li>CreateBoundOperation - transforms BoundOperation to EOperation on entity</li>
 *   <li>CreateBoundTransferOperation - transforms BoundTransferOperation to EOperation</li>
 *   <li>CreateUnboundOperation - transforms UnboundOperation to EOperation</li>
 * </ul>
 */
@hu.blackbelt.judo.zeta.annotation.TransformationContext(source = BoundOperation.class, target = EOperation.class)
public class OperationRules {

    /**
     * Default constructor required for TransformationRegistry.
     */
    public OperationRules() {
    }

    // =========================================================================
    // GUARDS
    // =========================================================================

    /**
     * Guard: element has documentation
     */
    public boolean hasDocumentation(EObject source, TransformationContext ctx) {
        if (source instanceof hu.blackbelt.judo.meta.psm.namespace.NamedElement) {
            String doc = ((hu.blackbelt.judo.meta.psm.namespace.NamedElement) source).getDocumentation();
            return doc != null && !doc.isEmpty();
        }
        return false;
    }

    /**
     * Guard: bound operation has input parameter
     */
    public boolean hasInput(EObject source, TransformationContext ctx) {
        if (source instanceof BoundOperation) {
            return ((BoundOperation) source).getInput() != null;
        }
        return false;
    }

    /**
     * Guard: bound operation has output parameter
     */
    public boolean hasOutput(EObject source, TransformationContext ctx) {
        if (source instanceof BoundOperation) {
            return ((BoundOperation) source).getOutput() != null;
        }
        return false;
    }

    /**
     * Guard: transfer operation has input parameter
     */
    public boolean hasTransferInput(EObject source, TransformationContext ctx) {
        if (source instanceof TransferOperation) {
            return ((TransferOperation) source).getInput() != null;
        }
        return false;
    }

    /**
     * Guard: transfer operation has output parameter
     */
    public boolean hasTransferOutput(EObject source, TransformationContext ctx) {
        if (source instanceof TransferOperation) {
            return ((TransferOperation) source).getOutput() != null;
        }
        return false;
    }

    /**
     * Guard: parameter is an input parameter (contained in a TransferOperation as input)
     */
    public boolean isInputParameter(EObject source, TransformationContext ctx) {
        if (source instanceof Parameter) {
            Parameter param = (Parameter) source;
            EObject container = param.eContainer();
            if (container instanceof TransferOperation) {
                return ((TransferOperation) container).getInput() == param;
            }
        }
        return false;
    }

    /**
     * Guard: operation is abstract
     */
    public boolean isAbstractOperation(EObject source, TransformationContext ctx) {
        if (source instanceof BoundOperation) {
            return ((BoundOperation) source).isAbstract();
        }
        return false;
    }

    /**
     * Guard: bound operation has implementation
     */
    public boolean hasBoundOperationImplementation(EObject source, TransformationContext ctx) {
        if (source instanceof BoundOperation) {
            return ((BoundOperation) source).getImplementation() != null;
        }
        return false;
    }

    /**
     * Guard: transfer operation has behaviour
     */
    public boolean hasBehaviour(EObject source, TransformationContext ctx) {
        if (source instanceof TransferOperation) {
            return ((TransferOperation) source).getBehaviour() != null;
        }
        return false;
    }

    // =========================================================================
    // BOUND OPERATION RULES
    // =========================================================================

    /**
     * rule CreateBoundOperation
     *     transform s : JUDOPSM!BoundOperation
     *     to t : ASM!EOperation
     *     extends CreateOperation
     */
    @TransformRule(name = "CreateBoundOperation", description = "Transform BoundOperation to EOperation")
    @Transform(type = BoundOperation.class)
    @To(type = EOperation.class)
    public TransformFunction<BoundOperation, EOperation> createBoundOperation() {
        return (s, ctx) -> {
            EOperation t = ctx.createTarget(EOperation.class);
            setId(t, "(psm/" + getId(s) + ")/BoundOperation");
            t.setName(s.getName());
            
            // Set output type and cardinality (from CreateOperation abstract rule in ETL)
            if (s.getOutput() != null) {
                if (s.getOutput().getType() != null) {
                    EClass outputType = ctx.equivalent(s.getOutput().getType(), EClass.class);
                    if (outputType != null) {
                        t.setEType(outputType);
                    }
                }
                if (s.getOutput().getCardinality() != null) {
                    t.setLowerBound(s.getOutput().getCardinality().getLower());
                    t.setUpperBound(s.getOutput().getCardinality().getUpper());
                }
            }
            
            // Add faults as exceptions (from CreateOperation abstract rule in ETL)
            for (var fault : s.getFaults()) {
                if (fault.getType() != null) {
                    EClass faultType = ctx.equivalent(fault.getType(), EClass.class);
                    if (faultType != null) {
                        t.getEExceptions().add(faultType);
                    }
                }
            }
            
            // Add to owning entity class
            EntityType owner = getEntityType(s);
            if (owner != null) {
                EClass ownerClass = ctx.equivalent(owner, EClass.class);
                if (ownerClass != null) {
                    ownerClass.getEOperations().add(t);
                }
            }
            
            return t;
        };
    }

    /**
     * rule CreateBoundOperationAnnotation
     *     transform s : JUDOPSM!BoundOperation
     *     to t : ASM!EAnnotation
     * 
     * Note: ETL uses "bound" annotation URI, not "boundOperation"
     */
    @TransformRule(name = "CreateBoundOperationAnnotation", description = "Add bound annotation to BoundOperation")
    @Transform(type = BoundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<BoundOperation, EAnnotation> createBoundOperationAnnotation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            setId(t, "(psm/" + getId(s) + ")/BoundOperationAnnotation");
            t.setSource(getAnnotationUri("bound"));
            addAnnotationDetail(t, "value", "true");
            
            // Add to equivalent operation
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            if (eOp != null) {
                eOp.getEAnnotations().add(t);
            }
            
            return t;
        };
    }

    /**
     * rule CreateInstanceRepresentationOfBoundOperation
     *     transform s : JUDOPSM!BoundOperation
     *     to t : ASM!EAnnotation
     */
    @TransformRule(name = "CreateInstanceRepresentationOfBoundOperation", description = "Add instanceRepresentation annotation")
    @Transform(type = BoundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<BoundOperation, EAnnotation> createInstanceRepresentationOfBoundOperation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            setId(t, "(psm/" + getId(s) + ")/InstanceRepresentationOfBoundOperation");
            t.setSource(getAnnotationUri("instanceRepresentation"));
            
            // Get the instance representation type
            if (s.getInstanceRepresentation() != null) {
                EClass instanceRep = ctx.equivalent(s.getInstanceRepresentation(), EClass.class);
                if (instanceRep != null) {
                    addAnnotationDetail(t, "value", getClassifierFQName(instanceRep));
                }
            }
            
            // Add to equivalent operation
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            if (eOp != null) {
                eOp.getEAnnotations().add(t);
            }
            
            return t;
        };
    }

    /**
     * rule CreateAbstractBoundOperationAnnotation
     *     transform s : JUDOPSM!BoundOperation
     *     to t : ASM!EAnnotation {
     *         guard: s.abstract
     *     }
     */
    @TransformRule(name = "CreateAbstractBoundOperationAnnotation", description = "Add abstract annotation")
    @Guard(method = "isAbstractOperation")
    @Transform(type = BoundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<BoundOperation, EAnnotation> createAbstractBoundOperationAnnotation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            setId(t, "(psm/" + getId(s) + ")/AbstractBoundOperationAnnotation");
            t.setSource(getAnnotationUri("abstract"));
            addAnnotationDetail(t, "value", "true");
            
            // Add to equivalent operation
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            if (eOp != null) {
                eOp.getEAnnotations().add(t);
            }
            
            return t;
        };
    }

    /**
     * rule CreateOutputParameterNameForBoundOperation
     *     transform s : JUDOPSM!BoundOperation
     *     to t : ASM!EAnnotation {
     *         guard: s.output.isDefined()
     *     }
     * 
     * ETL applies CreateOutputParameterName to OperationDeclaration which includes BoundOperation.
     */
    @TransformRule(name = "CreateOutputParameterNameForBoundOperation", description = "Add outputParameterName annotation for BoundOperation")
    @Guard(method = "hasOutput")
    @Transform(type = BoundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<BoundOperation, EAnnotation> createOutputParameterNameForBoundOperation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            setId(t, "(psm/" + getId(s) + ")/OutputParameterName");
            t.setSource(getAnnotationUri("outputParameterName"));
            addAnnotationDetail(t, "value", s.getOutput().getName());
            
            // Add to equivalent operation
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            if (eOp != null) {
                eOp.getEAnnotations().add(t);
            }
            
            return t;
        };
    }

    /**
     * rule CreateCustomImplementationAnnotationOnBoundOperation
     *     transform s : JUDOPSM!BoundOperation
     *     to t : ASM!EAnnotation {
     *         guard: s.implementation.isDefined()
     *     }
     */
    @TransformRule(name = "CreateCustomImplementationAnnotationOnBoundOperation", description = "Add customImplementation annotation to BoundOperation")
    @Guard(method = "hasBoundOperationImplementation")
    @Transform(type = BoundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<BoundOperation, EAnnotation> createCustomImplementationAnnotationOnBoundOperation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            setId(t, "(psm/" + getId(s) + ")/CustomImplementationAnnotationOnBoundOperation");
            t.setSource(getAnnotationUri("customImplementation"));
            addAnnotationDetail(t, "value", String.valueOf(s.getImplementation().isCustomImplementation()));
            
            // Add to equivalent operation
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            if (eOp != null) {
                eOp.getEAnnotations().add(t);
            }
            
            return t;
        };
    }

    /**
     * Guard: bound operation has implementation with script body
     */
    public boolean hasBoundOperationScriptBody(EObject source, TransformationContext ctx) {
        if (source instanceof BoundOperation) {
            BoundOperation op = (BoundOperation) source;
            return op.getImplementation() != null 
                    && op.getImplementation().getBody() != null 
                    && !op.getImplementation().getBody().trim().isEmpty();
        }
        return false;
    }

    /**
     * rule CreateScriptBodyAnnotationForBoundOperation
     *     transform s : JUDOPSM!BoundOperation
     *     to t : ASM!EAnnotation
     *     extends CreateScriptBodyAnnotation {
     *         guard: s.implementation.isDefined() and s.implementation.body.isDefined() and s.implementation.body.trim() <> ""
     *     }
     */
    @TransformRule(name = "CreateScriptBodyAnnotationForBoundOperation", description = "Add script annotation to BoundOperation")
    @Guard(method = "hasBoundOperationScriptBody")
    @Transform(type = BoundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<BoundOperation, EAnnotation> createScriptBodyAnnotationForBoundOperation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            setId(t, "(psm/" + getId(s) + ")/ScriptBodyAnnotationForBoundOperation");
            t.setSource(getAnnotationUri("script"));
            addAnnotationDetail(t, "body", s.getImplementation().getBody());
            
            // Add to equivalent operation
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            if (eOp != null) {
                eOp.getEAnnotations().add(t);
            }
            
            return t;
        };
    }

    /**
     * rule CreateBoundOperationInputParameter
     *     transform s : JUDOPSM!BoundOperation
     *     to t : ASM!EParameter {
     *         guard: s.input.isDefined()
     *     }
     */
    @TransformRule(name = "CreateBoundOperationInputParameter", description = "Create input parameter for bound operation")
    @Guard(method = "hasInput")
    @Transform(type = BoundOperation.class)
    @To(type = EParameter.class)
    public TransformFunction<BoundOperation, EParameter> createBoundOperationInputParameter() {
        return (s, ctx) -> {
            EParameter t = ctx.createTarget(EParameter.class);
            setId(t, "(psm/" + getId(s) + ")/BoundOperationInputParameter");
            t.setName(s.getInput().getName());
            
            // Set type
            if (s.getInput().getType() != null) {
                EClass paramType = ctx.equivalent(s.getInput().getType(), EClass.class);
                if (paramType != null) {
                    t.setEType(paramType);
                }
            }
            
            // Set cardinality
            if (s.getInput().getCardinality() != null) {
                t.setLowerBound(s.getInput().getCardinality().getLower());
                t.setUpperBound(s.getInput().getCardinality().getUpper());
            }
            
            // Add to equivalent operation
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            if (eOp != null) {
                eOp.getEParameters().add(t);
            }
            
            return t;
        };
    }

    // =========================================================================
    // TRANSFER OPERATION RULES
    // =========================================================================

    /**
     * rule CreateBoundTransferOperation
     *     transform s : JUDOPSM!BoundTransferOperation
     *     to t : ASM!EOperation
     */
    @TransformRule(name = "CreateBoundTransferOperation", description = "Transform BoundTransferOperation to EOperation")
    @Transform(type = BoundTransferOperation.class)
    @To(type = EOperation.class)
    public TransformFunction<BoundTransferOperation, EOperation> createBoundTransferOperation() {
        return (s, ctx) -> {
            EOperation t = ctx.createTarget(EOperation.class);
            setId(t, "(psm/" + getId(s) + ")/BoundTransferOperation");
            t.setName(s.getName());
            
            // Set output type and cardinality from binding
            if (s.getBinding() != null && s.getBinding().getOutput() != null) {
                if (s.getBinding().getOutput().getType() != null) {
                    EClass outputType = ctx.equivalent(s.getBinding().getOutput().getType(), EClass.class);
                    if (outputType != null) {
                        t.setEType(outputType);
                    }
                }
                if (s.getBinding().getOutput().getCardinality() != null) {
                    t.setLowerBound(s.getBinding().getOutput().getCardinality().getLower());
                    t.setUpperBound(s.getBinding().getOutput().getCardinality().getUpper());
                }
            }
            
            // Add faults from binding
            if (s.getBinding() != null) {
                for (var fault : s.getBinding().getFaults()) {
                    if (fault.getType() != null) {
                        EClass faultType = ctx.equivalent(fault.getType(), EClass.class);
                        if (faultType != null) {
                            t.getEExceptions().add(faultType);
                        }
                    }
                }
            }
            
            // Add binding annotation inline (matching ETL CreateBoundTransferOperation)
            if (s.getBinding() != null) {
                EAnnotation bindingAnnotation = createAnnotation(
                        t.eResource() != null ? t.eResource().getURIFragment(t) + "/BindingAnnotation" 
                                : "(psm/" + getId(s) + ")/BoundTransferOperation/BindingAnnotation",
                        getAnnotationUri("binding"));
                EOperation boundOp = ctx.equivalent(s.getBinding(), EOperation.class);
                if (boundOp != null) {
                    addAnnotationDetail(bindingAnnotation, "value", boundOp.getName());
                }
                t.getEAnnotations().add(bindingAnnotation);
            }
            
            // Add to owning transfer object class
            TransferObjectType owner = (TransferObjectType) s.eContainer();
            if (owner != null) {
                EClass ownerClass = ctx.equivalent(owner, EClass.class);
                if (ownerClass != null) {
                    ownerClass.getEOperations().add(t);
                }
            }
            
            return t;
        };
    }

    /**
     * rule CreateUnboundOperation
     *     transform s : JUDOPSM!UnboundOperation
     *     to t : ASM!EOperation
     */
    @TransformRule(name = "CreateUnboundOperation", description = "Transform UnboundOperation to EOperation")
    @Transform(type = UnboundOperation.class)
    @To(type = EOperation.class)
    public TransformFunction<UnboundOperation, EOperation> createUnboundOperation() {
        return (s, ctx) -> {
            EOperation t = ctx.createTarget(EOperation.class);
            setId(t, "(psm/" + getId(s) + ")/UnboundOperation");
            t.setName(s.getName());
            
            // Set output type and cardinality
            if (s.getOutput() != null) {
                if (s.getOutput().getType() != null) {
                    EClass outputType = ctx.equivalent(s.getOutput().getType(), EClass.class);
                    if (outputType != null) {
                        t.setEType(outputType);
                    }
                }
                if (s.getOutput().getCardinality() != null) {
                    t.setLowerBound(s.getOutput().getCardinality().getLower());
                    t.setUpperBound(s.getOutput().getCardinality().getUpper());
                }
            }
            
            // Add to owning transfer object class
            TransferObjectType owner = (TransferObjectType) s.eContainer();
            if (owner != null) {
                EClass ownerClass = ctx.equivalent(owner, EClass.class);
                if (ownerClass != null) {
                    ownerClass.getEOperations().add(t);
                }
            }
            
            return t;
        };
    }

    /**
     * rule CreateTransferOperationBehaviourAnnotation
     *     transform s : JUDOPSM!TransferOperation
     *     to t : ASM!EAnnotation {
     *         guard: s.behaviour.isDefined()
     *     }
     */
    @TransformRule(name = "CreateTransferOperationBehaviourAnnotation", description = "Add behaviour annotation")
    @Greedy
    @Guard(method = "hasBehaviour")
    @Transform(type = TransferOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferOperation, EAnnotation> createTransferOperationBehaviourAnnotation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            setId(t, "(psm/" + getId(s) + ")/BehaviourAnnotation");
            t.setSource(getAnnotationUri("behaviour"));
            
            TransferOperationBehaviour behaviour = s.getBehaviour();
            String typeValue = mapBehaviourType(behaviour, s);
            String ownerValue = mapBehaviourOwner(behaviour, ctx);
            
            addAnnotationDetail(t, "type", typeValue);
            addAnnotationDetail(t, "owner", ownerValue);
            
            // Add to equivalent operation
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            if (eOp != null) {
                eOp.getEAnnotations().add(t);
            }
            
            // For BoundTransferOperation, also add behaviour annotation to the binding (entity operation)
            if (s instanceof BoundTransferOperation) {
                BoundTransferOperation bto = (BoundTransferOperation) s;
                if (bto.getBinding() != null) {
                    EOperation bindingOp = ctx.equivalent(bto.getBinding(), EOperation.class);
                    if (bindingOp != null) {
                        EAnnotation bindingAnnotation = createAnnotation(
                                "(psm/" + getId(s) + ")/BehaviourAnnotation/BindingAnnotation",
                                getAnnotationUri("behaviour"));
                        addAnnotationDetail(bindingAnnotation, "type", typeValue);
                        addAnnotationDetail(bindingAnnotation, "owner", ownerValue);
                        bindingOp.getEAnnotations().add(bindingAnnotation);
                    }
                }
            }
            
            return t;
        };
    }
    
    /**
     * Map TransferOperationBehaviourType to the string value expected by ASM.
     */
    private String mapBehaviourType(TransferOperationBehaviour behaviour, TransferOperation operation) {
        TransferOperationBehaviourType type = behaviour.getBehaviourType();
        switch (type) {
            case LIST: return "list";
            case CREATE_INSTANCE: return "createInstance";
            case VALIDATE_CREATE: return "validateCreate";
            case REFRESH: return "refresh";
            case UPDATE_INSTANCE: return "updateInstance";
            case VALIDATE_UPDATE: return "validateUpdate";
            case DELETE_INSTANCE: return "deleteInstance";
            case SET_REFERENCE: return "setReference";
            case UNSET_REFERENCE: return "unsetReference";
            case ADD_REFERENCE: return "addReference";
            case REMOVE_REFERENCE: return "removeReference";
            case GET_RANGE:
                // Distinguish between getReferenceRange and getInputRange
                if (behaviour.getOwner() instanceof TransferObjectRelation) {
                    return "getReferenceRange";
                } else if (behaviour.getOwner() instanceof TransferOperation) {
                    return "getInputRange";
                }
                return "getRange";
            case GET_TEMPLATE: return "getTemplate";
            case GET_PRINCIPAL: return "getPrincipal";
            case GET_METADATA: return "getMetadata";
            case GET_UPLOAD_TOKEN: return "getUploadToken";
            case EXPORT: return "export";
            case VALIDATE_OPERATION_INPUT: return "validateOperationInput";
            default: return type.toString().toLowerCase();
        }
    }
    
    /**
     * Map behaviour owner to the FQ name expected by ASM.
     */
    private String mapBehaviourOwner(TransferOperationBehaviour behaviour, TransformationContext ctx) {
        if (behaviour.getOwner() == null) {
            return "";
        }
        
        TransferOperationBehaviourType type = behaviour.getBehaviourType();
        EObject owner = behaviour.getOwner();
        
        switch (type) {
            case GET_TEMPLATE:
            case GET_PRINCIPAL:
            case GET_METADATA:
            case REFRESH:
            case UPDATE_INSTANCE:
            case VALIDATE_UPDATE:
            case DELETE_INSTANCE:
                // Use classifier FQ name
                if (owner instanceof TransferObjectType) {
                    EClass ownerClass = ctx.equivalent(owner, EClass.class);
                    if (ownerClass != null) {
                        return getClassifierFQName(ownerClass);
                    }
                }
                break;
            case GET_UPLOAD_TOKEN:
                // Use attribute FQ name
                if (owner instanceof TransferAttribute) {
                    EAttribute ownerAttr = ctx.equivalent(owner, EAttribute.class);
                    if (ownerAttr != null) {
                        return getAttributeFQName(ownerAttr);
                    }
                }
                break;
            case GET_RANGE:
                if (owner instanceof TransferObjectRelation) {
                    EReference ownerRef = ctx.equivalent(owner, EReference.class);
                    if (ownerRef != null) {
                        return getReferenceFQName(ownerRef);
                    }
                } else if (owner instanceof TransferOperation) {
                    EOperation ownerOp = ctx.equivalent(owner, EOperation.class);
                    if (ownerOp != null) {
                        return getOperationFQName(ownerOp);
                    }
                }
                break;
            case VALIDATE_OPERATION_INPUT:
                if (owner instanceof TransferOperation) {
                    EOperation ownerOp = ctx.equivalent(owner, EOperation.class);
                    if (ownerOp != null) {
                        return getOperationFQName(ownerOp);
                    }
                }
                break;
            default:
                // Default: use reference FQ name
                if (owner instanceof TransferObjectRelation) {
                    EReference ownerRef = ctx.equivalent(owner, EReference.class);
                    if (ownerRef != null) {
                        return getReferenceFQName(ownerRef);
                    }
                }
                break;
        }
        
        // Fallback to qualified name for NamespaceElement
        if (owner instanceof NamespaceElement) {
            return getQualifiedName((NamespaceElement) owner);
        }
        return "";
    }

    /**
     * rule CreateInputParameter
     *     transform s : JUDOPSM!Parameter
     *     to t : ASM!EParameter {
     *         guard: s.isInput()
     *     }
     * 
     * Transforms a Parameter (that is an input) to EParameter.
     */
    @TransformRule(name = "CreateInputParameter", description = "Create input parameter from Parameter")
    @Greedy
    @Guard(method = "isInputParameter")
    @Transform(type = Parameter.class)
    @To(type = EParameter.class)
    public TransformFunction<Parameter, EParameter> createInputParameter() {
        return (s, ctx) -> {
            EParameter t = ctx.createTarget(EParameter.class);
            setId(t, "(psm/" + getId(s) + ")/InputParameter");
            t.setName(s.getName());
            
            // Set type
            if (s.getType() != null) {
                EClass paramType = ctx.equivalent(s.getType(), EClass.class);
                if (paramType != null) {
                    t.setEType(paramType);
                }
            }
            
            // Set cardinality
            if (s.getCardinality() != null) {
                t.setLowerBound(s.getCardinality().getLower());
                t.setUpperBound(s.getCardinality().getUpper());
            }
            
            // Add to equivalent operation (container is the TransferOperation)
            if (s.eContainer() instanceof TransferOperation) {
                TransferOperation op = (TransferOperation) s.eContainer();
                EOperation eOp = ctx.equivalent(op, EOperation.class);
                if (eOp != null) {
                    eOp.getEParameters().add(t);
                }
            }
            
            return t;
        };
    }

    // =========================================================================
    // ADDITIONAL TRANSFER OPERATION ANNOTATIONS
    // =========================================================================

    /**
     * Guard: transfer operation has implementation
     */
    public boolean hasImplementation(EObject source, TransformationContext ctx) {
        if (source instanceof TransferOperation) {
            return ((TransferOperation) source).getImplementation() != null;
        }
        return false;
    }

    /**
     * Guard: transfer operation has no implementation and no behaviour
     */
    public boolean hasNoImplementationAndNoBehaviour(EObject source, TransformationContext ctx) {
        if (source instanceof TransferOperation) {
            TransferOperation op = (TransferOperation) source;
            return op.getImplementation() == null && op.getBehaviour() == null;
        }
        return false;
    }

    /**
     * rule CreateStatefulAnnotationOnOperation
     *     transform s : JUDOPSM!TransferOperation
     *     to t : ASM!EAnnotation {
     *         guard: s.implementation.isDefined()
     *     }
     */
    @TransformRule(name = "CreateStatefulAnnotationOnOperation", description = "Add stateful annotation with implementation")
    @Greedy
    @Guard(method = "hasImplementation")
    @Transform(type = TransferOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferOperation, EAnnotation> createStatefulAnnotationOnOperation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            setId(t, "(psm/" + getId(s) + ")/StatefulAnnotationOnOperation");
            t.setSource(getAnnotationUri("stateful"));
            addAnnotationDetail(t, "value", String.valueOf(s.getImplementation().isStateful()));
            
            // Add to equivalent operation
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            if (eOp != null) {
                eOp.getEAnnotations().add(t);
            }
            
            return t;
        };
    }

    /**
     * rule CreateStatefulAnnotationOnOperationWithoutImplementationAndBehaviour
     *     transform s : JUDOPSM!TransferOperation
     *     to t : ASM!EAnnotation {
     *         guard: not s.implementation.isDefined() and not s.behaviour.isDefined()
     *     }
     */
    @TransformRule(name = "CreateStatefulAnnotationDefault", description = "Add default stateful annotation")
    @Greedy
    @Guard(method = "hasNoImplementationAndNoBehaviour")
    @Transform(type = TransferOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferOperation, EAnnotation> createStatefulAnnotationDefault() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            setId(t, "(psm/" + getId(s) + ")/StatefulAnnotationOnOperationWithoutImplementationAndBehaviour");
            t.setSource(getAnnotationUri("stateful"));
            addAnnotationDetail(t, "value", "true");
            
            // Add to equivalent operation
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            if (eOp != null) {
                eOp.getEAnnotations().add(t);
            }
            
            return t;
        };
    }

    /**
     * rule CreateCustomImplementationAnnotationOnOperation
     *     transform s : JUDOPSM!TransferOperation
     *     to t : ASM!EAnnotation {
     *         guard: s.implementation.isDefined()
     *     }
     */
    @TransformRule(name = "CreateCustomImplementationAnnotation", description = "Add customImplementation annotation")
    @Greedy
    @Guard(method = "hasImplementation")
    @Transform(type = TransferOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferOperation, EAnnotation> createCustomImplementationAnnotation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            setId(t, "(psm/" + getId(s) + ")/CustomImplementationAnnotationOnOperation");
            t.setSource(getAnnotationUri("customImplementation"));
            addAnnotationDetail(t, "value", String.valueOf(s.getImplementation().isCustomImplementation()));
            
            // Add to equivalent operation
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            if (eOp != null) {
                eOp.getEAnnotations().add(t);
            }
            
            return t;
        };
    }

    /**
     * Guard: unbound operation has implementation
     */
    public boolean hasUnboundOperationImplementation(EObject source, TransformationContext ctx) {
        if (source instanceof UnboundOperation) {
            return ((UnboundOperation) source).getImplementation() != null;
        }
        return false;
    }

    /**
     * Guard: unbound operation is an initializer
     */
    public boolean isInitializer(EObject source, TransformationContext ctx) {
        if (source instanceof UnboundOperation) {
            return ((UnboundOperation) source).isInitializer();
        }
        return false;
    }

    /**
     * rule CreateInitializerAnnotation
     *     transform s : JUDOPSM!UnboundOperation
     *     to t : ASM!EAnnotation {
     *         guard: s.initializer
     *     }
     */
    @TransformRule(name = "CreateInitializerAnnotation", description = "Add initializer annotation to UnboundOperation")
    @Guard(method = "isInitializer")
    @Transform(type = UnboundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<UnboundOperation, EAnnotation> createInitializerAnnotation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            setId(t, "(psm/" + getId(s) + ")/InitializerAnnotation");
            t.setSource(getAnnotationUri("initializer"));
            addAnnotationDetail(t, "value", "true");
            
            // Add to equivalent operation
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            if (eOp != null) {
                eOp.getEAnnotations().add(t);
            }
            
            return t;
        };
    }

    /**
     * Guard: unbound operation has implementation with script body
     */
    public boolean hasUnboundOperationScriptBody(EObject source, TransformationContext ctx) {
        if (source instanceof UnboundOperation) {
            UnboundOperation op = (UnboundOperation) source;
            return op.getImplementation() != null 
                    && op.getImplementation().getBody() != null 
                    && !op.getImplementation().getBody().trim().isEmpty();
        }
        return false;
    }

    /**
     * rule CreateScriptBodyAnnotationForUnboundOperation
     *     transform s : JUDOPSM!UnboundOperation
     *     to t : ASM!EAnnotation
     *     extends CreateScriptBodyAnnotation {
     *         guard: s.implementation.isDefined() and s.implementation.body.isDefined() and s.implementation.body.trim() <> ""
     *     }
     */
    @TransformRule(name = "CreateScriptBodyAnnotationForUnboundOperation", description = "Add script annotation to UnboundOperation")
    @Guard(method = "hasUnboundOperationScriptBody")
    @Transform(type = UnboundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<UnboundOperation, EAnnotation> createScriptBodyAnnotationForUnboundOperation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            setId(t, "(psm/" + getId(s) + ")/ScriptBodyAnnotationForUnboundOperation");
            t.setSource(getAnnotationUri("script"));
            addAnnotationDetail(t, "body", s.getImplementation().getBody());
            
            // Add to equivalent operation
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            if (eOp != null) {
                eOp.getEAnnotations().add(t);
            }
            
            return t;
        };
    }

    /**
     * rule CreateCustomImplementationAnnotationOnUnboundOperation
     *     transform s : JUDOPSM!UnboundOperation
     *     to t : ASM!EAnnotation {
     *         guard: s.implementation.isDefined()
     *     }
     * 
     * Note: ETL has both CreateCustomImplementationAnnotationOnOperation (for TransferOperation)
     * and CreateCustomImplementationAnnotationOnUnboundOperation (for UnboundOperation).
     * Both rules fire for UnboundOperation, creating 2 customImplementation annotations.
     * We replicate this behavior for compatibility.
     */
    @TransformRule(name = "CreateCustomImplementationAnnotationOnUnboundOperation", description = "Add customImplementation annotation to UnboundOperation")
    @Guard(method = "hasUnboundOperationImplementation")
    @Transform(type = UnboundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<UnboundOperation, EAnnotation> createCustomImplementationAnnotationOnUnboundOperation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            setId(t, "(psm/" + getId(s) + ")/CustomImplementationAnnotationOnUnboundOperation");
            t.setSource(getAnnotationUri("customImplementation"));
            addAnnotationDetail(t, "value", String.valueOf(s.getImplementation().isCustomImplementation()));
            
            // Add to equivalent operation
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            if (eOp != null) {
                eOp.getEAnnotations().add(t);
            }
            
            return t;
        };
    }

    /**
     * rule CreateOutputParameterName
     *     transform s : JUDOPSM!TransferOperation
     *     to t : ASM!EAnnotation {
     *         guard: s.output.isDefined()
     *     }
     */
    @TransformRule(name = "CreateOutputParameterName", description = "Add outputParameterName annotation")
    @Greedy
    @Guard(method = "hasTransferOutput")
    @Transform(type = TransferOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferOperation, EAnnotation> createOutputParameterName() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            setId(t, "(psm/" + getId(s) + ")/OutputParameterName");
            t.setSource(getAnnotationUri("outputParameterName"));
            addAnnotationDetail(t, "value", s.getOutput().getName());
            
            // Add to equivalent operation
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            if (eOp != null) {
                eOp.getEAnnotations().add(t);
            }
            
            return t;
        };
    }

    /**
     * rule CreateOperationPermissions
     *     transform s : JUDOPSM!TransferOperation
     *     to t : ASM!EAnnotation
     * 
     * Adds permissions annotation with update/delete flags.
     */
    @TransformRule(name = "CreateOperationPermissions", description = "Add permissions annotation to transfer operation")
    @Greedy
    @Transform(type = TransferOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferOperation, EAnnotation> createOperationPermissions() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            setId(t, "(psm/" + getId(s) + ")/OperationPermissions");
            t.setSource(getAnnotationUri("permissions"));
            addAnnotationDetail(t, "update", String.valueOf(s.isUpdateOnResult()));
            addAnnotationDetail(t, "delete", String.valueOf(s.isDeleteOnResult()));
            
            // Add to equivalent operation
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            if (eOp != null) {
                eOp.getEAnnotations().add(t);
            }
            
            return t;
        };
    }

    /**
     * rule CreateImmutableFlagForTransferOperation
     *     transform s : JUDOPSM!TransferOperation
     *     to t : ASM!EAnnotation
     * 
     * Adds immutable annotation to transfer operations.
     */
    @TransformRule(name = "CreateImmutableFlagForTransferOperation", description = "Add immutable annotation to transfer operation")
    @Greedy
    @Transform(type = TransferOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferOperation, EAnnotation> createImmutableFlagForTransferOperation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            setId(t, "(psm/" + getId(s) + ")/ImmutableAnnotationOnOperation");
            t.setSource(getAnnotationUri("immutable"));
            addAnnotationDetail(t, "value", String.valueOf(s.isImmutable()));
            
            // Add to equivalent operation
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            if (eOp != null) {
                eOp.getEAnnotations().add(t);
            }
            
            return t;
        };
    }

    /**
     * rule CreateBoundAnnotationForTransferOperation
     *     transform s : JUDOPSM!TransferOperation
     *     to t : ASM!EAnnotation
     * 
     * Adds bound annotation indicating whether operation is bound or unbound.
     */
    @TransformRule(name = "CreateBoundAnnotationForTransferOperation", description = "Add bound annotation to transfer operation")
    @Greedy
    @Transform(type = TransferOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferOperation, EAnnotation> createBoundAnnotationForTransferOperation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            setId(t, "(psm/" + getId(s) + ")/BoundOperationAnnotation");
            t.setSource(getAnnotationUri("bound"));
            // BoundTransferOperation is bound, UnboundOperation is not
            boolean isBound = s instanceof BoundTransferOperation;
            addAnnotationDetail(t, "value", String.valueOf(isBound));
            
            // Add to equivalent operation
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            if (eOp != null) {
                eOp.getEAnnotations().add(t);
            }
            
            return t;
        };
    }

    /**
     * rule CreateStatefulAnnotationOnOperationWithBehaviour
     *     transform s : JUDOPSM!TransferOperation
     *     to t : ASM!EAnnotation {
     *         guard: not s.implementation.isDefined() and s.behaviour.isDefined()
     *     }
     * 
     * Adds stateful annotation based on behaviour type.
     */
    @TransformRule(name = "CreateStatefulAnnotationWithBehaviour", description = "Add stateful annotation based on behaviour")
    @Greedy
    @Guard(method = "hasNoImplementationButHasBehaviour")
    @Transform(type = TransferOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferOperation, EAnnotation> createStatefulAnnotationWithBehaviour() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            setId(t, "(psm/" + getId(s) + ")/StatefulAnnotationOnOperationWithBehaviour");
            t.setSource(getAnnotationUri("stateful"));
            
            // Determine stateful value based on behaviour type
            TransferOperationBehaviourType behaviourType = s.getBehaviour().getBehaviourType();
            boolean stateful = true;
            switch (behaviourType) {
                case VALIDATE_CREATE:
                case VALIDATE_UPDATE:
                case LIST:
                case EXPORT:
                case GET_RANGE:
                case GET_TEMPLATE:
                case GET_PRINCIPAL:
                case GET_METADATA:
                case VALIDATE_OPERATION_INPUT:
                    stateful = false;
                    break;
                default:
                    stateful = true;
            }
            addAnnotationDetail(t, "value", String.valueOf(stateful));
            
            // Add to equivalent operation
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            if (eOp != null) {
                eOp.getEAnnotations().add(t);
            }
            
            return t;
        };
    }

    /**
     * Guard: transfer operation has no implementation but has behaviour
     */
    public boolean hasNoImplementationButHasBehaviour(EObject source, TransformationContext ctx) {
        if (source instanceof TransferOperation) {
            TransferOperation op = (TransferOperation) source;
            return op.getImplementation() == null && op.getBehaviour() != null;
        }
        return false;
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    /**
     * Gets the owning EntityType for an element.
     */
    private EntityType getEntityType(EObject element) {
        EObject container = element.eContainer();
        while (container != null) {
            if (container instanceof EntityType) {
                return (EntityType) container;
            }
            container = container.eContainer();
        }
        return null;
    }

    /**
     * Gets the fully qualified name of an EClassifier.
     */
    private String getClassifierFQName(EClassifier classifier) {
        if (classifier == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        EPackage pkg = classifier.getEPackage();
        while (pkg != null) {
            if (sb.length() > 0) {
                sb.insert(0, ".");
            }
            sb.insert(0, pkg.getName());
            pkg = pkg.getESuperPackage();
        }
        if (sb.length() > 0) {
            sb.append(".");
        }
        sb.append(classifier.getName());
        return sb.toString();
    }
    
    /**
     * Gets the fully qualified name of an EReference.
     */
    private String getReferenceFQName(EReference reference) {
        if (reference == null) {
            return "";
        }
        EClass container = reference.getEContainingClass();
        if (container != null) {
            return getClassifierFQName(container) + "#" + reference.getName();
        }
        return reference.getName();
    }
    
    /**
     * Gets the fully qualified name of an EOperation.
     */
    private String getOperationFQName(EOperation operation) {
        if (operation == null) {
            return "";
        }
        EClass container = operation.getEContainingClass();
        if (container != null) {
            return getClassifierFQName(container) + "#" + operation.getName();
        }
        return operation.getName();
    }
    
    /**
     * Gets the fully qualified name of an EAttribute.
     */
    private String getAttributeFQName(EAttribute attribute) {
        if (attribute == null) {
            return "";
        }
        EClass container = attribute.getEContainingClass();
        if (container != null) {
            return getClassifierFQName(container) + "#" + attribute.getName();
        }
        return attribute.getName();
    }
}
