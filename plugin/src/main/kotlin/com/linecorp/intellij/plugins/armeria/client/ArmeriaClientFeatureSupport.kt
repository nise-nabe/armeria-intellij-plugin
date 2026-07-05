package com.linecorp.intellij.plugins.armeria.client

import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaDecoratorSupport
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRouteTargetExtractor
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression

internal object ArmeriaClientFeatureSupport {
  private val CLIENT_FEATURE_METHODS = setOf("decorator", "endpointGroup")

  fun extractJavaFeatures(expression: PsiMethodCallExpression): List<String> {
    val features = linkedSetOf<String>()
    var current: PsiExpression? = expression
    while (current != null) {
      val call = PsiTreeUtil.getParentOfType(current, PsiMethodCallExpression::class.java, false) ?: break
      val methodName = call.methodExpression.referenceName ?: break
      if (methodName !in CLIENT_FEATURE_METHODS) {
        current = call.methodExpression.qualifierExpression
        continue
      }
      when (methodName) {
        "decorator" -> {
          val argument = call.argumentList.expressions.firstOrNull()
          ArmeriaDecoratorSupport.labelDecorator(ArmeriaRouteTargetExtractor.extractTarget(argument))
              .takeIf { it.isNotBlank() }
              ?.let(features::add)
        }
        "endpointGroup" -> {
          val argument = call.argumentList.expressions.firstOrNull()
          val label = ArmeriaRouteTargetExtractor.extractTarget(argument).ifBlank { "EndpointGroup" }
          features += "EndpointGroup: $label"
        }
      }
      current = call.methodExpression.qualifierExpression
    }
    return features.toList()
  }

  fun extractKotlinFeatures(call: KtCallExpression): List<String> {
    val features = linkedSetOf<String>()
    var current: KtExpression? = call
    while (current != null) {
      val expression = current.parent as? KtDotQualifiedExpression ?: break
      val selector = expression.selectorExpression as? KtCallExpression ?: break
      val methodName = selector.calleeExpression?.text ?: break
      if (methodName !in CLIENT_FEATURE_METHODS) {
        current = expression.receiverExpression
        continue
      }
      val argument = selector.valueArguments.firstOrNull()?.getArgumentExpression()
      when (methodName) {
        "decorator" -> {
          ArmeriaDecoratorSupport.labelDecorator(argument?.text.orEmpty())
              .takeIf { it.isNotBlank() }
              ?.let(features::add)
        }
        "endpointGroup" -> {
          val label = argument?.text?.takeIf { it.isNotBlank() } ?: "EndpointGroup"
          features += "EndpointGroup: $label"
        }
      }
      current = expression.receiverExpression
    }
    return features.toList()
  }
}
