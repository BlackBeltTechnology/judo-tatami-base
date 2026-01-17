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
import static hu.blackbelt.judo.tatami.psm2asm.zeta.Psm2AsmRuleNames.*;

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
    @TransformRule(name = CREATE_BOUND_OPERATION, description = "Transform BoundOperation to EOperation")
    @Transform(type = BoundOperation.class)
    @To(type = EOperation.class)
    public TransformFunction<BoundOperation, EOperation> createBoundOperation() {
        return (s, ctx) -> {
            EOperation t = ctx.createTarget(EOperation.class);
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
                        synchronized (t.getEExceptions()) {
                            t.getEExceptions().add(faultType);
                        }
                    }
                }
            }

            // Add to owning entity class (thread-safe)
            EntityType owner = getEntityType(s);
            if (owner != null) {
                EClass ownerClass = ctx.equivalent(owner, EClass.class);
                addOperation(ownerClass, t);
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
    @TransformRule(name = CREATE_BOUND_OPERATION_ANNOTATION, description = "Add bound annotation to BoundOperation")
    @Transform(type = BoundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<BoundOperation, EAnnotation> createBoundOperationAnnotation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("bound"));
            addAnnotationDetail(t, "value", "true");
            
            // Add to equivalent operation (thread-safe)
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            addAnnotation(eOp, t);

            return t;
        };
    }

    /**
     * rule CreateInstanceRepresentationOfBoundOperation
     *     transform s : JUDOPSM!BoundOperation
     *     to t : ASM!EAnnotation
     */
    @TransformRule(name = CREATE_INSTANCE_REPRESENTATION_OF_BOUND_OPERATION, description = "Add instanceRepresentation annotation")
    @Transform(type = BoundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<BoundOperation, EAnnotation> createInstanceRepresentationOfBoundOperation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("instanceRepresentation"));

            // Get the instance representation type from PSM source (not ASM target)
            // to ensure complete package hierarchy is available during parallel execution
            if (s.getInstanceRepresentation() != null) {
                addAnnotationDetail(t, "value", getQualifiedName(s.getInstanceRepresentation()));
            }

            // Add to equivalent operation (thread-safe)
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            addAnnotation(eOp, t);

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
    @TransformRule(name = CREATE_ABSTRACT_ANNOTATION_FOR_BOUND_OPERATION, description = "Add abstract annotation")
    @Guard(method = "isAbstractOperation")
    @Transform(type = BoundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<BoundOperation, EAnnotation> createAbstractBoundOperationAnnotation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("abstract"));
            addAnnotationDetail(t, "value", "true");
            
            // Add to equivalent operation (thread-safe)
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            addAnnotation(eOp, t);

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
    @TransformRule(name = CREATE_OUTPUT_PARAMETER_NAME_FOR_BOUND_OPERATION, description = "Add outputParameterName annotation for BoundOperation")
    @Guard(method = "hasOutput")
    @Transform(type = BoundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<BoundOperation, EAnnotation> createOutputParameterNameForBoundOperation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("outputParameterName"));
            addAnnotationDetail(t, "value", s.getOutput().getName());
            
            // Add to equivalent operation (thread-safe)
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            addAnnotation(eOp, t);

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
    @TransformRule(name = CREATE_CUSTOM_IMPLEMENTATION_ANNOTATION_ON_BOUND_OPERATION, description = "Add customImplementation annotation to BoundOperation")
    @Guard(method = "hasBoundOperationImplementation")
    @Transform(type = BoundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<BoundOperation, EAnnotation> createCustomImplementationAnnotationOnBoundOperation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("customImplementation"));
            addAnnotationDetail(t, "value", String.valueOf(s.getImplementation().isCustomImplementation()));
            
            // Add to equivalent operation (thread-safe)
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            addAnnotation(eOp, t);

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
    @TransformRule(name = CREATE_SCRIPT_BODY_ANNOTATION_FOR_BOUND_OPERATION, description = "Add script annotation to BoundOperation")
    @Guard(method = "hasBoundOperationScriptBody")
    @Transform(type = BoundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<BoundOperation, EAnnotation> createScriptBodyAnnotationForBoundOperation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("script"));
            addAnnotationDetail(t, "body", s.getImplementation().getBody());
            
            // Add to equivalent operation (thread-safe)
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            addAnnotation(eOp, t);

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
    @TransformRule(name = CREATE_BOUND_OPERATION_INPUT_PARAMETER, description = "Create input parameter for bound operation")
    @Guard(method = "hasInput")
    @Transform(type = BoundOperation.class)
    @To(type = EParameter.class)
    public TransformFunction<BoundOperation, EParameter> createBoundOperationInputParameter() {
        return (s, ctx) -> {
            EParameter t = ctx.createTarget(EParameter.class);
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
            
            // Add to equivalent operation (thread-safe)
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            addParameter(eOp, t);

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
    @TransformRule(name = CREATE_BOUND_TRANSFER_OPERATION, description = "Transform BoundTransferOperation to EOperation")
    @Greedy
    @Transform(type = BoundTransferOperation.class)
    @To(type = EOperation.class)
    public TransformFunction<BoundTransferOperation, EOperation> createBoundTransferOperation() {
        return (s, ctx) -> {
            EOperation t = ctx.createTarget(EOperation.class);
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
                            synchronized (t.getEExceptions()) {
                                t.getEExceptions().add(faultType);
                            }
                        }
                    }
                }
            }

            // Add binding annotation inline (matching ETL CreateBoundTransferOperation, thread-safe)
            if (s.getBinding() != null) {
                EAnnotation bindingAnnotation = createAnnotation(
                        t.eResource() != null ? t.eResource().getURIFragment(t) + "/BindingAnnotation"
                                : "(psm/" + getId(s) + ")/BoundTransferOperation/BindingAnnotation",
                        getAnnotationUri("binding"));
                EOperation boundOp = ctx.equivalent(s.getBinding(), EOperation.class);
                if (boundOp != null) {
                    addAnnotationDetail(bindingAnnotation, "value", boundOp.getName());
                }
                addAnnotation(t, bindingAnnotation);
            }

            // Add to owning transfer object class (thread-safe)
            TransferObjectType owner = (TransferObjectType) s.eContainer();
            if (owner != null) {
                EClass ownerClass = ctx.equivalent(owner, EClass.class);
                addOperation(ownerClass, t);
            }
            
            return t;
        };
    }

    /**
     * rule CreateUnboundOperation
     *     transform s : JUDOPSM!UnboundOperation
     *     to t : ASM!EOperation
     */
    @TransformRule(name = CREATE_UNBOUND_OPERATION, description = "Transform UnboundOperation to EOperation")
    @Greedy
    @Transform(type = UnboundOperation.class)
    @To(type = EOperation.class)
    public TransformFunction<UnboundOperation, EOperation> createUnboundOperation() {
        return (s, ctx) -> {
            EOperation t = ctx.createTarget(EOperation.class);
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
            
            // Add to owning transfer object class (thread-safe)
            TransferObjectType owner = (TransferObjectType) s.eContainer();
            if (owner != null) {
                EClass ownerClass = ctx.equivalent(owner, EClass.class);
                addOperation(ownerClass, t);
            }

            return t;
        };
    }

    /**
     * Guard: BoundTransferOperation has behaviour
     */
    public boolean hasBoundTransferBehaviour(EObject source, TransformationContext ctx) {
        if (source instanceof BoundTransferOperation) {
            return ((BoundTransferOperation) source).getBehaviour() != null;
        }
        return false;
    }

    /**
     * Guard: UnboundOperation has behaviour
     */
    public boolean hasUnboundBehaviour(EObject source, TransformationContext ctx) {
        if (source instanceof UnboundOperation) {
            return ((UnboundOperation) source).getBehaviour() != null;
        }
        return false;
    }

    /**
     * rule CreateBehaviourAnnotationForBoundTransferOperation
     *     transform s : JUDOPSM!BoundTransferOperation
     *     to t : ASM!EAnnotation {
     *         guard: s.behaviour.isDefined()
     *     }
     * Split from CreateTransferOperationBehaviourAnnotation for type-based filtering optimization.
     * Includes binding annotation for the bound operation.
     */
    @TransformRule(name = CREATE_BEHAVIOUR_ANNOTATION_FOR_BTO, description = "Add behaviour annotation to BoundTransferOperation")
    @Greedy
    @Guard(method = "hasBoundTransferBehaviour")
    @Transform(type = BoundTransferOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<BoundTransferOperation, EAnnotation> createBehaviourAnnotationForBoundTransferOp() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("behaviour"));

            TransferOperationBehaviour behaviour = s.getBehaviour();
            String typeValue = mapBehaviourType(behaviour, s);
            String ownerValue = mapBehaviourOwner(behaviour, ctx);

            addAnnotationDetail(t, "type", typeValue);
            addAnnotationDetail(t, "owner", ownerValue);

            // Add to equivalent operation (thread-safe)
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            addAnnotation(eOp, t);

            // Also add behaviour annotation to the binding (entity operation, thread-safe)
            if (s.getBinding() != null) {
                EOperation bindingOp = ctx.equivalent(s.getBinding(), EOperation.class);
                if (bindingOp != null) {
                    EAnnotation bindingAnnotation = createAnnotation(
                            "(psm/" + getId(s) + ")/BehaviourAnnotation/BindingAnnotation",
                            getAnnotationUri("behaviour"));
                    addAnnotationDetail(bindingAnnotation, "type", typeValue);
                    addAnnotationDetail(bindingAnnotation, "owner", ownerValue);
                    addAnnotation(bindingOp, bindingAnnotation);
                }
            }

            return t;
        };
    }

    /**
     * rule CreateBehaviourAnnotationForUnboundOperation
     *     transform s : JUDOPSM!UnboundOperation
     *     to t : ASM!EAnnotation {
     *         guard: s.behaviour.isDefined()
     *     }
     * Split from CreateTransferOperationBehaviourAnnotation for type-based filtering optimization.
     */
    @TransformRule(name = CREATE_BEHAVIOUR_ANNOTATION_FOR_UO, description = "Add behaviour annotation to UnboundOperation")
    @Greedy
    @Guard(method = "hasUnboundBehaviour")
    @Transform(type = UnboundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<UnboundOperation, EAnnotation> createBehaviourAnnotationForUnboundOp() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("behaviour"));

            TransferOperationBehaviour behaviour = s.getBehaviour();
            String typeValue = mapBehaviourType(behaviour, s);
            String ownerValue = mapBehaviourOwner(behaviour, ctx);

            addAnnotationDetail(t, "type", typeValue);
            addAnnotationDetail(t, "owner", ownerValue);

            // Add to equivalent operation (thread-safe)
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            addAnnotation(eOp, t);

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
     * <p>
     * Uses PSM source elements to compute FQNames instead of ASM target elements,
     * because the ASM package hierarchy may not be fully established during parallel
     * transformation execution.
     * </p>
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
                // Use classifier FQ name from PSM source
                if (owner instanceof TransferObjectType) {
                    return getQualifiedName((TransferObjectType) owner);
                }
                break;
            case GET_UPLOAD_TOKEN:
                // Use attribute FQ name from PSM source
                if (owner instanceof TransferAttribute) {
                    return getPsmAttributeFQName((TransferAttribute) owner);
                }
                break;
            case GET_RANGE:
                // For getReferenceRange, the owner is the TransferObjectType and the relation
                // comes from behaviour.getRelation()
                if (owner instanceof TransferObjectType && behaviour.getRelation() != null) {
                    return getQualifiedName((TransferObjectType) owner) + "#" + behaviour.getRelation().getName();
                } else if (owner instanceof TransferObjectRelation) {
                    return getPsmReferenceFQName((TransferObjectRelation) owner);
                } else if (owner instanceof TransferOperation) {
                    return getPsmOperationFQName((TransferOperation) owner);
                }
                break;
            case VALIDATE_OPERATION_INPUT:
                if (owner instanceof TransferOperation) {
                    return getPsmOperationFQName((TransferOperation) owner);
                }
                break;
            default:
                // Default: use reference FQ name from PSM source
                // For LIST, CREATE_INSTANCE, etc. operations, the owner is the TransferObjectType
                // and the relation name comes from behaviour.getRelation()
                if (owner instanceof TransferObjectType && behaviour.getRelation() != null) {
                    return getQualifiedName((TransferObjectType) owner) + "#" + behaviour.getRelation().getName();
                } else if (owner instanceof TransferObjectRelation) {
                    return getPsmReferenceFQName((TransferObjectRelation) owner);
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
     * Gets the fully qualified name of a PSM TransferObjectRelation.
     * Format: container_FQN#relation_name
     */
    private String getPsmReferenceFQName(TransferObjectRelation relation) {
        if (relation == null) {
            return "";
        }
        EObject container = relation.eContainer();
        if (container instanceof TransferObjectType) {
            return getQualifiedName((TransferObjectType) container) + "#" + relation.getName();
        }
        return relation.getName();
    }

    /**
     * Gets the fully qualified name of a PSM TransferOperation.
     * Format: container_FQN#operation_name
     */
    private String getPsmOperationFQName(TransferOperation operation) {
        if (operation == null) {
            return "";
        }
        EObject container = operation.eContainer();
        if (container instanceof TransferObjectType) {
            return getQualifiedName((TransferObjectType) container) + "#" + operation.getName();
        }
        return operation.getName();
    }

    /**
     * Gets the fully qualified name of a PSM TransferAttribute.
     * Format: container_FQN#attribute_name
     */
    private String getPsmAttributeFQName(TransferAttribute attribute) {
        if (attribute == null) {
            return "";
        }
        EObject container = attribute.eContainer();
        if (container instanceof TransferObjectType) {
            return getQualifiedName((TransferObjectType) container) + "#" + attribute.getName();
        }
        return attribute.getName();
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
    @TransformRule(name = CREATE_INPUT_PARAMETER, description = "Create input parameter from Parameter")
    @Greedy
    @Guard(method = "isInputParameter")
    @Transform(type = Parameter.class)
    @To(type = EParameter.class)
    public TransformFunction<Parameter, EParameter> createInputParameter() {
        return (s, ctx) -> {
            EParameter t = ctx.createTarget(EParameter.class);
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
            
            // Add to equivalent operation (container is the TransferOperation, thread-safe)
            if (s.eContainer() instanceof TransferOperation) {
                TransferOperation op = (TransferOperation) s.eContainer();
                EOperation eOp = ctx.equivalent(op, EOperation.class);
                addParameter(eOp, t);
            }
            
            return t;
        };
    }

    // =========================================================================
    // ADDITIONAL TRANSFER OPERATION ANNOTATIONS
    // =========================================================================

    /**
     * Guard: transfer operation has implementation
     * ETL DIFFERENCE: ETL uses s.implementation.isDefined() which matches both TransferOperation and UnboundOperation.
     * Zeta guard matches the same semantics - no need to exclude UnboundOperation.
     */
    public boolean hasImplementation(EObject source, TransformationContext ctx) {
        if (source instanceof TransferOperation) {
            return ((TransferOperation) source).getImplementation() != null;
        }
        return false;
    }

    /**
     * Guard: transfer operation has no implementation and no behaviour
     * ETL DIFFERENCE: ETL uses not s.implementation.isDefined() and not s.behaviour.isDefined().
     * Zeta guard matches the same semantics for all TransferOperation subtypes.
     */
    public boolean hasNoImplementationAndNoBehaviour(EObject source, TransformationContext ctx) {
        if (source instanceof TransferOperation) {
            TransferOperation op = (TransferOperation) source;
            return op.getImplementation() == null && op.getBehaviour() == null;
        }
        return false;
    }

    /**
     * Guard: BoundTransferOperation has implementation
     */
    public boolean hasBoundTransferImplementation(EObject source, TransformationContext ctx) {
        if (source instanceof BoundTransferOperation) {
            return ((BoundTransferOperation) source).getImplementation() != null;
        }
        return false;
    }

    /**
     * rule CreateStatefulAnnotationOnBoundTransferOperation
     *     transform s : JUDOPSM!BoundTransferOperation
     *     to t : ASM!EAnnotation {
     *         guard: s.implementation.isDefined()
     *     }
     * Split from CreateStatefulAnnotationOnOperation for type-based filtering optimization.
     */
    @TransformRule(name = CREATE_STATEFUL_ANNOTATION_ON_BTO, description = "Add stateful annotation with implementation to BoundTransferOperation")
    @Greedy
    @Guard(method = "hasBoundTransferImplementation")
    @Transform(type = BoundTransferOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<BoundTransferOperation, EAnnotation> createStatefulAnnotationOnBoundTransferOp() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("stateful"));
            addAnnotationDetail(t, "value", String.valueOf(s.getImplementation().isStateful()));

            // Add to equivalent operation (thread-safe)
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            addAnnotation(eOp, t);

            return t;
        };
    }

    /**
     * rule CreateStatefulAnnotationOnUnboundOperation
     *     transform s : JUDOPSM!UnboundOperation
     *     to t : ASM!EAnnotation {
     *         guard: s.implementation.isDefined()
     *     }
     * Split from CreateStatefulAnnotationOnOperation for type-based filtering optimization.
     * Note: This is separate from the existing UnboundOperation-specific rules.
     */
    @TransformRule(name = CREATE_STATEFUL_ANNOTATION_ON_UO, description = "Add stateful annotation with implementation to UnboundOperation")
    @Greedy
    @Guard(method = "hasUnboundOperationImplementation")
    @Transform(type = UnboundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<UnboundOperation, EAnnotation> createStatefulAnnotationOnUnboundOp() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("stateful"));
            addAnnotationDetail(t, "value", String.valueOf(s.getImplementation().isStateful()));

            // Add to equivalent operation (thread-safe)
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            addAnnotation(eOp, t);

            return t;
        };
    }

    /**
     * Guard: BoundTransferOperation has no implementation and no behaviour
     */
    public boolean hasBTONoImplementationAndNoBehaviour(EObject source, TransformationContext ctx) {
        if (source instanceof BoundTransferOperation) {
            BoundTransferOperation op = (BoundTransferOperation) source;
            return op.getImplementation() == null && op.getBehaviour() == null;
        }
        return false;
    }

    /**
     * Guard: UnboundOperation has no implementation and no behaviour
     */
    public boolean hasUONoImplementationAndNoBehaviour(EObject source, TransformationContext ctx) {
        if (source instanceof UnboundOperation) {
            UnboundOperation op = (UnboundOperation) source;
            return op.getImplementation() == null && op.getBehaviour() == null;
        }
        return false;
    }

    /**
     * rule CreateStatefulAnnotationDefaultForBoundTransferOp
     *     transform s : JUDOPSM!BoundTransferOperation
     *     to t : ASM!EAnnotation {
     *         guard: not s.implementation.isDefined() and not s.behaviour.isDefined()
     *     }
     * Split from CreateStatefulAnnotationDefault for type-based filtering optimization.
     */
    @TransformRule(name = CREATE_STATEFUL_ANNOTATION_DEFAULT_FOR_BTO, description = "Add default stateful annotation to BoundTransferOperation")
    @Greedy
    @Guard(method = "hasBTONoImplementationAndNoBehaviour")
    @Transform(type = BoundTransferOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<BoundTransferOperation, EAnnotation> createStatefulAnnotationDefaultForBTO() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("stateful"));
            addAnnotationDetail(t, "value", "true");

            // Add to equivalent operation (thread-safe)
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            addAnnotation(eOp, t);

            return t;
        };
    }

    /**
     * rule CreateStatefulAnnotationDefaultForUnboundOp
     *     transform s : JUDOPSM!UnboundOperation
     *     to t : ASM!EAnnotation {
     *         guard: not s.implementation.isDefined() and not s.behaviour.isDefined()
     *     }
     * Split from CreateStatefulAnnotationDefault for type-based filtering optimization.
     */
    @TransformRule(name = CREATE_STATEFUL_ANNOTATION_DEFAULT_FOR_UO, description = "Add default stateful annotation to UnboundOperation")
    @Greedy
    @Guard(method = "hasUONoImplementationAndNoBehaviour")
    @Transform(type = UnboundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<UnboundOperation, EAnnotation> createStatefulAnnotationDefaultForUO() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("stateful"));
            addAnnotationDetail(t, "value", "true");

            // Add to equivalent operation (thread-safe)
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            addAnnotation(eOp, t);

            return t;
        };
    }

    /**
     * rule CreateCustomImplementationForBoundTransferOp
     *     transform s : JUDOPSM!BoundTransferOperation
     *     to t : ASM!EAnnotation {
     *         guard: s.implementation.isDefined()
     *     }
     * Split from CreateCustomImplementationAnnotation for type-based filtering optimization.
     */
    @TransformRule(name = CREATE_CUSTOM_IMPLEMENTATION_FOR_BTO, description = "Add customImplementation annotation to BoundTransferOperation")
    @Greedy
    @Guard(method = "hasBoundTransferImplementation")
    @Transform(type = BoundTransferOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<BoundTransferOperation, EAnnotation> createCustomImplementationForBTO() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("customImplementation"));
            addAnnotationDetail(t, "value", String.valueOf(s.getImplementation().isCustomImplementation()));

            // Add to equivalent operation (thread-safe)
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            addAnnotation(eOp, t);

            return t;
        };
    }

    /**
     * rule CreateCustomImplementationForUnboundOp
     *     transform s : JUDOPSM!UnboundOperation
     *     to t : ASM!EAnnotation {
     *         guard: s.implementation.isDefined()
     *     }
     * Split from CreateCustomImplementationAnnotation for type-based filtering optimization.
     * Note: UnboundOperation already has a separate createCustomImplementationAnnotationOnUnboundOperation,
     * but ETL also applies CreateCustomImplementationAnnotationOnOperation to all TransferOperations.
     */
    @TransformRule(name = CREATE_CUSTOM_IMPLEMENTATION_FOR_UO, description = "Add customImplementation annotation to UnboundOperation (TransferOperation path)")
    @Greedy
    @Guard(method = "hasUnboundOperationImplementation")
    @Transform(type = UnboundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<UnboundOperation, EAnnotation> createCustomImplementationForUO() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("customImplementation"));
            addAnnotationDetail(t, "value", String.valueOf(s.getImplementation().isCustomImplementation()));

            // Add to equivalent operation (thread-safe)
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            addAnnotation(eOp, t);

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
    @TransformRule(name = CREATE_INITIALIZER_ANNOTATION, description = "Add initializer annotation to UnboundOperation")
    @Guard(method = "isInitializer")
    @Transform(type = UnboundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<UnboundOperation, EAnnotation> createInitializerAnnotation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("initializer"));
            addAnnotationDetail(t, "value", "true");
            
            // Add to equivalent operation (thread-safe)
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            addAnnotation(eOp, t);

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
    @TransformRule(name = CREATE_SCRIPT_BODY_ANNOTATION_FOR_UNBOUND_OPERATION, description = "Add script annotation to UnboundOperation")
    @Guard(method = "hasUnboundOperationScriptBody")
    @Transform(type = UnboundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<UnboundOperation, EAnnotation> createScriptBodyAnnotationForUnboundOperation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("script"));
            addAnnotationDetail(t, "body", s.getImplementation().getBody());
            
            // Add to equivalent operation (thread-safe)
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            addAnnotation(eOp, t);

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
    @TransformRule(name = CREATE_CUSTOM_IMPLEMENTATION_ANNOTATION_ON_UNBOUND_OPERATION, description = "Add customImplementation annotation to UnboundOperation")
    @Guard(method = "hasUnboundOperationImplementation")
    @Transform(type = UnboundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<UnboundOperation, EAnnotation> createCustomImplementationAnnotationOnUnboundOperation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("customImplementation"));
            addAnnotationDetail(t, "value", String.valueOf(s.getImplementation().isCustomImplementation()));
            
            // Add to equivalent operation (thread-safe)
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            addAnnotation(eOp, t);

            return t;
        };
    }

    /**
     * Guard: BoundTransferOperation has output parameter
     */
    public boolean hasBoundTransferOutput(EObject source, TransformationContext ctx) {
        if (source instanceof BoundTransferOperation) {
            return ((BoundTransferOperation) source).getOutput() != null;
        }
        return false;
    }

    /**
     * Guard: UnboundOperation has output parameter
     */
    public boolean hasUnboundOutput(EObject source, TransformationContext ctx) {
        if (source instanceof UnboundOperation) {
            return ((UnboundOperation) source).getOutput() != null;
        }
        return false;
    }

    /**
     * rule CreateOutputParameterNameForBoundTransferOperation
     *     transform s : JUDOPSM!BoundTransferOperation
     *     to t : ASM!EAnnotation {
     *         guard: s.output.isDefined()
     *     }
     * Split from CreateOutputParameterName for type-based filtering optimization.
     */
    @TransformRule(name = CREATE_OUTPUT_PARAMETER_NAME_FOR_BTO, description = "Add outputParameterName annotation to BoundTransferOperation")
    @Greedy
    @Guard(method = "hasBoundTransferOutput")
    @Transform(type = BoundTransferOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<BoundTransferOperation, EAnnotation> createOutputParameterNameForBoundTransferOp() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("outputParameterName"));
            addAnnotationDetail(t, "value", s.getOutput().getName());

            // Add to equivalent operation (thread-safe)
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            addAnnotation(eOp, t);

            return t;
        };
    }

    /**
     * rule CreateOutputParameterNameForUnboundOperation
     *     transform s : JUDOPSM!UnboundOperation
     *     to t : ASM!EAnnotation {
     *         guard: s.output.isDefined()
     *     }
     * Split from CreateOutputParameterName for type-based filtering optimization.
     */
    @TransformRule(name = CREATE_OUTPUT_PARAMETER_NAME_FOR_UO, description = "Add outputParameterName annotation to UnboundOperation")
    @Greedy
    @Guard(method = "hasUnboundOutput")
    @Transform(type = UnboundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<UnboundOperation, EAnnotation> createOutputParameterNameForUnboundOp() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("outputParameterName"));
            addAnnotationDetail(t, "value", s.getOutput().getName());

            // Add to equivalent operation (thread-safe)
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            addAnnotation(eOp, t);

            return t;
        };
    }

    /**
     * rule CreateOperationPermissionsForBoundTransferOperation
     *     transform s : JUDOPSM!BoundTransferOperation
     *     to t : ASM!EAnnotation
     *
     * Adds permissions annotation with update/delete flags.
     * Split from CreateOperationPermissions for type-based filtering optimization.
     */
    @TransformRule(name = CREATE_OPERATION_PERMISSIONS_FOR_BTO, description = "Add permissions annotation to BoundTransferOperation")
    @Greedy
    @Transform(type = BoundTransferOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<BoundTransferOperation, EAnnotation> createOperationPermissionsForBoundTransferOperation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("permissions"));
            addAnnotationDetail(t, "update", String.valueOf(s.isUpdateOnResult()));
            addAnnotationDetail(t, "delete", String.valueOf(s.isDeleteOnResult()));

            // Add to equivalent operation (thread-safe)
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            addAnnotation(eOp, t);

            return t;
        };
    }

    /**
     * rule CreateOperationPermissionsForUnboundOperation
     *     transform s : JUDOPSM!UnboundOperation
     *     to t : ASM!EAnnotation
     *
     * Adds permissions annotation with update/delete flags.
     * Split from CreateOperationPermissions for type-based filtering optimization.
     */
    @TransformRule(name = CREATE_OPERATION_PERMISSIONS_FOR_UO, description = "Add permissions annotation to UnboundOperation")
    @Greedy
    @Transform(type = UnboundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<UnboundOperation, EAnnotation> createOperationPermissionsForUnboundOperation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("permissions"));
            addAnnotationDetail(t, "update", String.valueOf(s.isUpdateOnResult()));
            addAnnotationDetail(t, "delete", String.valueOf(s.isDeleteOnResult()));

            // Add to equivalent operation (thread-safe)
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            addAnnotation(eOp, t);

            return t;
        };
    }

    /**
     * rule CreateImmutableFlagForBoundTransferOperation
     *     transform s : JUDOPSM!BoundTransferOperation
     *     to t : ASM!EAnnotation
     *
     * Adds immutable annotation to BoundTransferOperation.
     * Split from CreateImmutableFlagForTransferOperation for type-based filtering optimization.
     */
    @TransformRule(name = CREATE_IMMUTABLE_FLAG_FOR_BTO, description = "Add immutable annotation to BoundTransferOperation")
    @Greedy
    @Transform(type = BoundTransferOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<BoundTransferOperation, EAnnotation> createImmutableFlagForBoundTransferOperation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("immutable"));
            addAnnotationDetail(t, "value", String.valueOf(s.isImmutable()));

            // Add to equivalent operation (thread-safe)
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            addAnnotation(eOp, t);

            return t;
        };
    }

    /**
     * rule CreateImmutableFlagForUnboundOperation
     *     transform s : JUDOPSM!UnboundOperation
     *     to t : ASM!EAnnotation
     *
     * Adds immutable annotation to UnboundOperation.
     * Split from CreateImmutableFlagForTransferOperation for type-based filtering optimization.
     */
    @TransformRule(name = CREATE_IMMUTABLE_FLAG_FOR_UO, description = "Add immutable annotation to UnboundOperation")
    @Greedy
    @Transform(type = UnboundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<UnboundOperation, EAnnotation> createImmutableFlagForUnboundOperation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("immutable"));
            addAnnotationDetail(t, "value", String.valueOf(s.isImmutable()));

            // Add to equivalent operation (thread-safe)
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            addAnnotation(eOp, t);

            return t;
        };
    }

    /**
     * rule CreateBoundAnnotationForBoundTransferOperation
     *     transform s : JUDOPSM!BoundTransferOperation
     *     to t : ASM!EAnnotation
     *
     * Adds bound annotation with value "true" for BoundTransferOperation.
     * Split from CreateBoundAnnotationForTransferOperation for type-based filtering optimization.
     */
    @TransformRule(name = CREATE_BOUND_ANNOTATION_FOR_BOUND_TRANSFER_OPERATION, description = "Add bound=true annotation to BoundTransferOperation")
    @Greedy
    @Transform(type = BoundTransferOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<BoundTransferOperation, EAnnotation> createBoundAnnotationForBoundTransferOperation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("bound"));
            addAnnotationDetail(t, "value", "true");

            // Add to equivalent operation (thread-safe)
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            addAnnotation(eOp, t);

            return t;
        };
    }

    /**
     * rule CreateBoundAnnotationForUnboundOperation
     *     transform s : JUDOPSM!UnboundOperation
     *     to t : ASM!EAnnotation
     *
     * Adds bound annotation with value "false" for UnboundOperation.
     * Split from CreateBoundAnnotationForTransferOperation for type-based filtering optimization.
     */
    @TransformRule(name = CREATE_BOUND_ANNOTATION_FOR_UNBOUND_OPERATION, description = "Add bound=false annotation to UnboundOperation")
    @Greedy
    @Transform(type = UnboundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<UnboundOperation, EAnnotation> createBoundAnnotationForUnboundOperation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("bound"));
            addAnnotationDetail(t, "value", "false");

            // Add to equivalent operation (thread-safe)
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            addAnnotation(eOp, t);

            return t;
        };
    }

    /**
     * Guard: BoundTransferOperation has no implementation but has behaviour
     */
    public boolean hasBTONoImplementationButHasBehaviour(EObject source, TransformationContext ctx) {
        if (source instanceof BoundTransferOperation) {
            BoundTransferOperation op = (BoundTransferOperation) source;
            return op.getImplementation() == null && op.getBehaviour() != null;
        }
        return false;
    }

    /**
     * Guard: UnboundOperation has no implementation but has behaviour
     */
    public boolean hasUONoImplementationButHasBehaviour(EObject source, TransformationContext ctx) {
        if (source instanceof UnboundOperation) {
            UnboundOperation op = (UnboundOperation) source;
            return op.getImplementation() == null && op.getBehaviour() != null;
        }
        return false;
    }

    /**
     * Helper method to determine stateful value based on behaviour type.
     */
    private boolean isStatefulBehaviour(TransferOperationBehaviourType behaviourType) {
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
                return false;
            default:
                return true;
        }
    }

    /**
     * rule CreateStatefulWithBehaviourForBoundTransferOp
     *     transform s : JUDOPSM!BoundTransferOperation
     *     to t : ASM!EAnnotation {
     *         guard: not s.implementation.isDefined() and s.behaviour.isDefined()
     *     }
     * Split from CreateStatefulAnnotationWithBehaviour for type-based filtering optimization.
     */
    @TransformRule(name = CREATE_STATEFUL_WITH_BEHAVIOUR_FOR_BTO, description = "Add stateful annotation based on behaviour to BoundTransferOperation")
    @Greedy
    @Guard(method = "hasBTONoImplementationButHasBehaviour")
    @Transform(type = BoundTransferOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<BoundTransferOperation, EAnnotation> createStatefulWithBehaviourForBTO() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("stateful"));

            boolean stateful = isStatefulBehaviour(s.getBehaviour().getBehaviourType());
            addAnnotationDetail(t, "value", String.valueOf(stateful));

            // Add to equivalent operation (thread-safe)
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            addAnnotation(eOp, t);

            return t;
        };
    }

    /**
     * rule CreateStatefulWithBehaviourForUnboundOp
     *     transform s : JUDOPSM!UnboundOperation
     *     to t : ASM!EAnnotation {
     *         guard: not s.implementation.isDefined() and s.behaviour.isDefined()
     *     }
     * Split from CreateStatefulAnnotationWithBehaviour for type-based filtering optimization.
     */
    @TransformRule(name = CREATE_STATEFUL_WITH_BEHAVIOUR_FOR_UO, description = "Add stateful annotation based on behaviour to UnboundOperation")
    @Greedy
    @Guard(method = "hasUONoImplementationButHasBehaviour")
    @Transform(type = UnboundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<UnboundOperation, EAnnotation> createStatefulWithBehaviourForUO() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("stateful"));

            boolean stateful = isStatefulBehaviour(s.getBehaviour().getBehaviourType());
            addAnnotationDetail(t, "value", String.valueOf(stateful));

            // Add to equivalent operation (thread-safe)
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            addAnnotation(eOp, t);

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

    /**
     * Guard: bound operation has documentation
     */
    public boolean hasBoundOperationDocumentation(EObject source, TransformationContext ctx) {
        if (source instanceof BoundOperation) {
            String doc = ((BoundOperation) source).getDocumentation();
            return doc != null && !doc.isEmpty();
        }
        return false;
    }

    /**
     * Guard: transfer operation has documentation
     */
    public boolean hasTransferOperationDocumentation(EObject source, TransformationContext ctx) {
        if (source instanceof TransferOperation) {
            String doc = ((TransferOperation) source).getDocumentation();
            return doc != null && !doc.isEmpty();
        }
        return false;
    }

    /**
     * Guard: input parameter has documentation
     */
    public boolean hasInputParameterDocumentation(EObject source, TransformationContext ctx) {
        if (source instanceof Parameter) {
            Parameter param = (Parameter) source;
            EObject container = param.eContainer();
            if (container instanceof TransferOperation) {
                if (((TransferOperation) container).getInput() == param) {
                    String doc = param.getDocumentation();
                    return doc != null && !doc.isEmpty();
                }
            }
        }
        return false;
    }

    /**
     * Guard: output parameter has documentation (for TransferOperation)
     */
    public boolean hasOutputParameterDocumentation(EObject source, TransformationContext ctx) {
        if (source instanceof TransferOperation) {
            TransferOperation op = (TransferOperation) source;
            if (op.getOutput() != null) {
                String doc = op.getOutput().getDocumentation();
                return doc != null && !doc.isEmpty();
            }
        }
        return false;
    }

    /**
     * Guard: bound operation output parameter has documentation
     */
    public boolean hasBoundOutputParameterDocumentation(EObject source, TransformationContext ctx) {
        if (source instanceof BoundOperation) {
            BoundOperation op = (BoundOperation) source;
            if (op.getOutput() != null) {
                String doc = op.getOutput().getDocumentation();
                return doc != null && !doc.isEmpty();
            }
        }
        return false;
    }

    /**
     * Guard: bound operation input parameter has documentation
     */
    public boolean hasBoundInputParameterDocumentation(EObject source, TransformationContext ctx) {
        if (source instanceof BoundOperation) {
            BoundOperation op = (BoundOperation) source;
            if (op.getInput() != null) {
                String doc = op.getInput().getDocumentation();
                return doc != null && !doc.isEmpty();
            }
        }
        return false;
    }

    /**
     * Guard: transfer operation has inputRange defined
     * ETL: s.inputRange.isDefined()
     */
    public boolean hasInputRange(EObject source, TransformationContext ctx) {
        if (source instanceof TransferOperation) {
            return ((TransferOperation) source).getInputRange() != null;
        }
        return false;
    }

    // =========================================================================
    // DOCUMENTATION ANNOTATION RULES
    // =========================================================================

    /**
     * rule CreateDocumentationAnnotationForBoundOperation
     *     transform s : JUDOPSM!BoundOperation
     *     to t : ASM!EAnnotation {
     *         guard: s.documentation.isDefined()
     *     }
     */
    @TransformRule(name = CREATE_DOCUMENTATION_ANNOTATION_FOR_BOUND_OPERATION, description = "Add documentation annotation to BoundOperation")
    @Guard(method = "hasBoundOperationDocumentation")
    @Transform(type = BoundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<BoundOperation, EAnnotation> createDocumentationAnnotationForBoundOperation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("documentation"));
            addAnnotationDetail(t, "value", s.getDocumentation());

            // Add to equivalent operation (thread-safe)
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            addAnnotation(eOp, t);

            return t;
        };
    }

    /**
     * Guard: BoundTransferOperation has documentation
     */
    public boolean hasBTODocumentation(EObject source, TransformationContext ctx) {
        if (source instanceof BoundTransferOperation) {
            String doc = ((BoundTransferOperation) source).getDocumentation();
            return doc != null && !doc.isEmpty();
        }
        return false;
    }

    /**
     * Guard: UnboundOperation has documentation
     */
    public boolean hasUODocumentation(EObject source, TransformationContext ctx) {
        if (source instanceof UnboundOperation) {
            String doc = ((UnboundOperation) source).getDocumentation();
            return doc != null && !doc.isEmpty();
        }
        return false;
    }

    /**
     * rule CreateDocumentationForBoundTransferOp
     *     transform s : JUDOPSM!BoundTransferOperation
     *     to t : ASM!EAnnotation {
     *         guard: s.documentation.isDefined()
     *     }
     * Split from CreateDocumentationAnnotationForTransferOperation for type-based filtering optimization.
     */
    @TransformRule(name = CREATE_DOCUMENTATION_FOR_BTO, description = "Add documentation annotation to BoundTransferOperation")
    @Greedy
    @Guard(method = "hasBTODocumentation")
    @Transform(type = BoundTransferOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<BoundTransferOperation, EAnnotation> createDocumentationForBTO() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("documentation"));
            addAnnotationDetail(t, "value", s.getDocumentation());

            // Add to equivalent operation (thread-safe)
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            addAnnotation(eOp, t);

            return t;
        };
    }

    /**
     * rule CreateDocumentationForUnboundOp
     *     transform s : JUDOPSM!UnboundOperation
     *     to t : ASM!EAnnotation {
     *         guard: s.documentation.isDefined()
     *     }
     * Split from CreateDocumentationAnnotationForTransferOperation for type-based filtering optimization.
     */
    @TransformRule(name = CREATE_DOCUMENTATION_FOR_UO, description = "Add documentation annotation to UnboundOperation")
    @Greedy
    @Guard(method = "hasUODocumentation")
    @Transform(type = UnboundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<UnboundOperation, EAnnotation> createDocumentationForUO() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("documentation"));
            addAnnotationDetail(t, "value", s.getDocumentation());

            // Add to equivalent operation (thread-safe)
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            addAnnotation(eOp, t);

            return t;
        };
    }

    /**
     * rule CreateDocumentationAnnotationForInputParameter
     *     transform s : JUDOPSM!Parameter
     *     to t : ASM!EAnnotation {
     *         guard: s.isInput() and s.documentation.isDefined()
     *     }
     */
    @TransformRule(name = CREATE_DOCUMENTATION_ANNOTATION_FOR_INPUT_PARAMETER, description = "Add documentation annotation to input parameter")
    @Greedy
    @Guard(method = "hasInputParameterDocumentation")
    @Transform(type = Parameter.class)
    @To(type = EAnnotation.class)
    public TransformFunction<Parameter, EAnnotation> createDocumentationAnnotationForInputParameter() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("documentation"));
            addAnnotationDetail(t, "value", s.getDocumentation());

            // Add to equivalent parameter (thread-safe)
            EParameter eParam = ctx.equivalent(s, EParameter.class);
            addAnnotation(eParam, t);

            return t;
        };
    }

    /**
     * Guard: BoundTransferOperation output parameter has documentation
     */
    public boolean hasBTOOutputParameterDocumentation(EObject source, TransformationContext ctx) {
        if (source instanceof BoundTransferOperation) {
            BoundTransferOperation op = (BoundTransferOperation) source;
            if (op.getOutput() != null) {
                String doc = op.getOutput().getDocumentation();
                return doc != null && !doc.isEmpty();
            }
        }
        return false;
    }

    /**
     * Guard: UnboundOperation output parameter has documentation
     */
    public boolean hasUOOutputParameterDocumentation(EObject source, TransformationContext ctx) {
        if (source instanceof UnboundOperation) {
            UnboundOperation op = (UnboundOperation) source;
            if (op.getOutput() != null) {
                String doc = op.getOutput().getDocumentation();
                return doc != null && !doc.isEmpty();
            }
        }
        return false;
    }

    /**
     * rule CreateOutputParamDocumentationForBTO
     *     transform s : JUDOPSM!BoundTransferOperation
     *     to t : ASM!EAnnotation {
     *         guard: s.output.isDefined() and s.output.documentation.isDefined()
     *     }
     * Split from CreateDocumentationAnnotationForOutputParameter for type-based filtering optimization.
     */
    @TransformRule(name = CREATE_OUTPUT_PARAM_DOCUMENTATION_FOR_BTO, description = "Add output parameter documentation to BoundTransferOperation")
    @Greedy
    @Guard(method = "hasBTOOutputParameterDocumentation")
    @Transform(type = BoundTransferOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<BoundTransferOperation, EAnnotation> createOutputParamDocumentationForBTO() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("outputParameterDocumentation"));
            addAnnotationDetail(t, "value", s.getOutput().getDocumentation());

            // Add to equivalent operation (thread-safe)
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            addAnnotation(eOp, t);

            return t;
        };
    }

    /**
     * rule CreateOutputParamDocumentationForUO
     *     transform s : JUDOPSM!UnboundOperation
     *     to t : ASM!EAnnotation {
     *         guard: s.output.isDefined() and s.output.documentation.isDefined()
     *     }
     * Split from CreateDocumentationAnnotationForOutputParameter for type-based filtering optimization.
     */
    @TransformRule(name = CREATE_OUTPUT_PARAM_DOCUMENTATION_FOR_UO, description = "Add output parameter documentation to UnboundOperation")
    @Greedy
    @Guard(method = "hasUOOutputParameterDocumentation")
    @Transform(type = UnboundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<UnboundOperation, EAnnotation> createOutputParamDocumentationForUO() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("outputParameterDocumentation"));
            addAnnotationDetail(t, "value", s.getOutput().getDocumentation());

            // Add to equivalent operation (thread-safe)
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            addAnnotation(eOp, t);

            return t;
        };
    }

    /**
     * rule CreateDocumentationAnnotationForBoundOutputParameter
     *     transform s : JUDOPSM!BoundOperation
     *     to t : ASM!EAnnotation {
     *         guard: s.output.isDefined() and s.output.documentation.isDefined()
     *     }
     */
    @TransformRule(name = CREATE_DOCUMENTATION_ANNOTATION_FOR_BOUND_OUTPUT_PARAMETER, description = "Add output parameter documentation annotation for BoundOperation")
    @Guard(method = "hasBoundOutputParameterDocumentation")
    @Transform(type = BoundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<BoundOperation, EAnnotation> createDocumentationAnnotationForBoundOutputParameter() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("outputParameterDocumentation"));
            addAnnotationDetail(t, "value", s.getOutput().getDocumentation());

            // Add to equivalent operation (thread-safe)
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            addAnnotation(eOp, t);

            return t;
        };
    }

    /**
     * rule CreateDocumentationAnnotationForBoundInputParameter
     *     transform s : JUDOPSM!BoundOperation
     *     to t : ASM!EAnnotation {
     *         guard: s.input.isDefined() and s.input.documentation.isDefined()
     *     }
     */
    @TransformRule(name = CREATE_DOCUMENTATION_ANNOTATION_FOR_BOUND_INPUT_PARAMETER, description = "Add input parameter documentation annotation for BoundOperation")
    @Guard(method = "hasBoundInputParameterDocumentation")
    @Transform(type = BoundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<BoundOperation, EAnnotation> createDocumentationAnnotationForBoundInputParameter() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("inputParameterDocumentation"));
            addAnnotationDetail(t, "value", s.getInput().getDocumentation());

            // Add to equivalent parameter (thread-safe)
            EParameter eParam = ctx.equivalent(s, EParameter.class);
            if (eParam != null) {
                addAnnotation(eParam, t);
            } else {
                // Fallback: add to operation if parameter not found
                EOperation eOp = ctx.equivalent(s, EOperation.class);
                addAnnotation(eOp, t);
            }

            return t;
        };
    }

    /**
     * Guard: BoundTransferOperation has inputRange defined
     */
    public boolean hasBTOInputRange(EObject source, TransformationContext ctx) {
        if (source instanceof BoundTransferOperation) {
            return ((BoundTransferOperation) source).getInputRange() != null;
        }
        return false;
    }

    /**
     * Guard: UnboundOperation has inputRange defined
     */
    public boolean hasUOInputRange(EObject source, TransformationContext ctx) {
        if (source instanceof UnboundOperation) {
            return ((UnboundOperation) source).getInputRange() != null;
        }
        return false;
    }

    /**
     * rule CreateInputRangeForBTO
     *     transform s : JUDOPSM!BoundTransferOperation
     *     to t : ASM!EAnnotation {
     *         guard: s.inputRange.isDefined()
     *     }
     * Split from CreateTransferOperationInputRangeAnnotation for type-based filtering optimization.
     */
    @TransformRule(name = CREATE_INPUT_RANGE_FOR_BTO, description = "Add inputRange annotation to BoundTransferOperation")
    @Greedy
    @Guard(method = "hasBTOInputRange")
    @Transform(type = BoundTransferOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<BoundTransferOperation, EAnnotation> createInputRangeForBTO() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("inputRange"));

            // Use PSM source element for FQ name to ensure complete package hierarchy
            // during parallel execution
            addAnnotationDetail(t, "value", getPsmReferenceFQName(s.getInputRange()));

            // Add to equivalent operation (thread-safe)
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            addAnnotation(eOp, t);

            return t;
        };
    }

    /**
     * rule CreateInputRangeForUO
     *     transform s : JUDOPSM!UnboundOperation
     *     to t : ASM!EAnnotation {
     *         guard: s.inputRange.isDefined()
     *     }
     * Split from CreateTransferOperationInputRangeAnnotation for type-based filtering optimization.
     */
    @TransformRule(name = CREATE_INPUT_RANGE_FOR_UO, description = "Add inputRange annotation to UnboundOperation")
    @Greedy
    @Guard(method = "hasUOInputRange")
    @Transform(type = UnboundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<UnboundOperation, EAnnotation> createInputRangeForUO() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("inputRange"));

            // Use PSM source element for FQ name to ensure complete package hierarchy
            // during parallel execution
            addAnnotationDetail(t, "value", getPsmReferenceFQName(s.getInputRange()));

            // Add to equivalent operation (thread-safe)
            EOperation eOp = ctx.equivalent(s, EOperation.class);
            addAnnotation(eOp, t);

            return t;
        };
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

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
