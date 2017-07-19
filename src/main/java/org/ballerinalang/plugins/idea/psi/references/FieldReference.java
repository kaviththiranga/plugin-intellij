/*
 *  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.ballerinalang.plugins.idea.psi.references;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import org.ballerinalang.plugins.idea.completion.BallerinaCompletionUtils;
import org.ballerinalang.plugins.idea.psi.FieldDefinitionNode;
import org.ballerinalang.plugins.idea.psi.IdentifierPSINode;
import org.ballerinalang.plugins.idea.psi.NameReferenceNode;
import org.ballerinalang.plugins.idea.psi.StatementNode;
import org.ballerinalang.plugins.idea.psi.StructDefinitionNode;
import org.ballerinalang.plugins.idea.psi.VariableDefinitionNode;
import org.ballerinalang.plugins.idea.psi.impl.BallerinaPsiImplUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public class FieldReference extends BallerinaElementReference {

    public FieldReference(@NotNull IdentifierPSINode element) {
        super(element);
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        // Get the current element.
        IdentifierPSINode identifier = getElement();
        // Get the parent element.
        PsiElement parent = identifier.getParent();

        PsiElement prevSibling;
        // If the current statement is not completed properly, the parent will be a StatementNode. This is used to
        // resolve multi level structs. Eg: user.name.<caret>
        if (parent instanceof StatementNode) {
            // Get the previous element.
            PsiElement prevVisibleLeaf = PsiTreeUtil.prevVisibleLeaf(parent);
            if (prevVisibleLeaf == null) {
                return null;
            }
            // Previous leaf element should be "." if the references are correctly defined.
            if (!".".equals(prevVisibleLeaf.getText())) {
                return null;
            }
            // Get the prevSibling. This is used to resolve the current field.
            prevSibling = PsiTreeUtil.prevVisibleLeaf(prevVisibleLeaf);
            if (prevSibling == null) {
                return null;
            }
        } else if (parent instanceof NameReferenceNode) {
            prevSibling = PsiTreeUtil.prevVisibleLeaf(parent);
            if (prevSibling != null && ".".equals(prevSibling.getText())) {
                prevSibling = PsiTreeUtil.prevVisibleLeaf(prevSibling);
            }
        } else {
            // If the current statement is correctly resolved, that means all the fields are identified properly.
            // Get the prevSibling. This is used to resolve the current field.
            prevSibling = PsiTreeUtil.prevVisibleLeaf(parent);
        }

        // If the prevSibling is null, we return from this method because we cannot resolve the element.
        if (prevSibling == null) {
            return null;
        }

        // We get the reference at end. This is because struct field access can be multiple levels deep.
        // Eg: user.name.first - If the current node is 'first', then the previous node will be 'user.name'. If
        // we get the reference at the beginning, we we get the reference for 'user'. But we want to resolve the
        // 'name' field first. That is why we get the reference at the end.
        PsiReference variableReference = prevSibling.findReferenceAt(prevSibling.getTextLength());
        if (variableReference == null) {
            return null;
        }
        // Resolve the reference. The resolved element can be an identifier of either a struct of a field
        // depending on the current node.
        // Eg: user.name.firstName - If the current node is 'name', resolved element will be a struct definition. if
        // the current element is 'firstName', then the resolved element will be a field definition.
        PsiElement resolvedElement = variableReference.resolve();
        if (resolvedElement == null) {
            return null;
        }

        // Get the parent of the resolved element.
        PsiElement resolvedElementParent = resolvedElement.getParent();
        StructDefinitionNode structDefinitionNode = null;
        // Resolve the corresponding resolvedElementParent to get the struct definition.
        if (resolvedElementParent instanceof VariableDefinitionNode) {
            // Resolve the Type of the VariableDefinitionNode to get the corresponding struct.
            // Eg: User user = {}
            //     In here, "User" is resolved and struct identifier is returned.
            structDefinitionNode = BallerinaPsiImplUtil.resolveStructFromDefinitionNode
                    (((VariableDefinitionNode) resolvedElementParent));
        } else if (resolvedElementParent instanceof FieldDefinitionNode) {
            // If the resolvedElementParent is of type FieldDefinitionNode, that means we need to resolve the type of
            // the field to get the struct definition.
            // Eg: user.name.firstName - In here, if we want to resolve the 'firstName' we will get the 'Name name;'
            // field. So we need to resolve the type of the field which is 'Name'. Then we will get the Name struct.
            // Then we need to get the 'firstName' field from that.
            structDefinitionNode =
                    BallerinaPsiImplUtil.resolveField(((FieldDefinitionNode) resolvedElementParent));
        }
        if (structDefinitionNode == null) {
            return null;
        }
        // Resolve the field and return the resolved element.
        return structDefinitionNode.resolve(identifier);
    }

    @NotNull
    @Override
    public Object[] getVariants() {
        IdentifierPSINode identifier = getElement();
        PsiElement prevVisibleLeaf = PsiTreeUtil.prevVisibleLeaf(identifier);
        // Todo - remove hard coded "."
        if (prevVisibleLeaf == null || !".".equals(prevVisibleLeaf.getText())) {
            return new LookupElement[0];
        }
        PsiElement previousField = PsiTreeUtil.prevVisibleLeaf(prevVisibleLeaf);
        if (previousField == null) {
            return new LookupElement[0];
        }
        PsiReference reference = previousField.findReferenceAt(0);
        if (reference == null) {
            return new LookupElement[0];
        }

        // Todo - use util method
        PsiElement resolvedElement = reference.resolve();
        if (resolvedElement == null || !(resolvedElement instanceof IdentifierPSINode)) {
            return new LookupElement[0];
        }
        PsiElement resolvedElementParent = resolvedElement.getParent();
        StructDefinitionNode structDefinitionNode = null;
        // Resolve the corresponding resolvedElementParent to get the struct definition.
        if (resolvedElementParent instanceof VariableDefinitionNode) {
            structDefinitionNode = BallerinaPsiImplUtil.resolveStructFromDefinitionNode
                    (((VariableDefinitionNode) resolvedElementParent));
        } else if (resolvedElementParent instanceof FieldDefinitionNode) {
            structDefinitionNode =
                    BallerinaPsiImplUtil.resolveField(((FieldDefinitionNode) resolvedElementParent));
        }
        if (structDefinitionNode == null) {
            return new LookupElement[0];
        }
        Collection<FieldDefinitionNode> fieldDefinitionNodes =
                PsiTreeUtil.findChildrenOfType(structDefinitionNode, FieldDefinitionNode.class);

        List<LookupElement> results = BallerinaCompletionUtils.createFieldLookupElements(fieldDefinitionNodes,
                (IdentifierPSINode) resolvedElement, null);
        return results.toArray(new LookupElement[results.size()]);
    }
}