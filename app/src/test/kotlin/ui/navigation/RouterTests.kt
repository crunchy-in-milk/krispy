package org.jellyfin.androidtv.ui.navigation

import androidx.compose.runtime.mutableStateListOf
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RouterTests : FunSpec({
	test("repeated back events return to the root without emptying the navigation stack") {
		val root = RouteContext("/", emptyMap())
		val customization = RouteContext("/customization", emptyMap())
		val backStack = mutableStateListOf(root, customization)
		val router = Router(emptyMap(), backStack)

		router.back()
		backStack.toList().shouldBe(listOf(root))
		router.back()
		router.back()
		backStack.toList().shouldBe(listOf(root))
	}
})
