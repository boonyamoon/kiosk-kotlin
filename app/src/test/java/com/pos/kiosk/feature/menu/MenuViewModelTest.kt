package com.pos.kiosk.feature.menu

import com.pos.kiosk.TestData
import com.pos.kiosk.core.common.AppError
import com.pos.kiosk.core.common.AppResult
import com.pos.kiosk.data.repository.InMemoryOrderSessionRepository
import com.pos.kiosk.domain.model.BranchInfo
import com.pos.kiosk.domain.model.Category
import com.pos.kiosk.domain.model.DisplayImage
import com.pos.kiosk.domain.model.KioskDisplay
import com.pos.kiosk.domain.model.KioskConfig
import com.pos.kiosk.domain.model.Product
import com.pos.kiosk.domain.model.ProductDetail
import com.pos.kiosk.domain.model.Sku
import com.pos.kiosk.domain.repository.ConfigRepository
import com.pos.kiosk.domain.repository.MenuRepository
import com.pos.kiosk.domain.usecase.AddToCartUseCase
import com.pos.kiosk.domain.usecase.OptionSelectionRules
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MenuViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val unavailable = MutableStateFlow<Set<String>>(emptySet())
    private val menu = mockk<MenuRepository> { every { unavailableProductIds } returns unavailable }
    private val configFlow = MutableStateFlow<KioskConfig?>(null)
    private val config = mockk<ConfigRepository> { every { config } returns configFlow }
    private val session = InMemoryOrderSessionRepository()
    private val rules = OptionSelectionRules()

    private val coffee = Category("c-coffee", "กาแฟ")
    private val bakery = Category("c-bakery", "เบเกอรี่")
    private val latte = product("p-latte", coffee.id, needsSheet = true)
    private val tea = product("p-tea", coffee.id, needsSheet = true)
    private val croissant = product("p-croissant", bakery.id, needsSheet = false)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun product(id: String, categoryId: String, needsSheet: Boolean) = Product(
        id = id,
        name = id,
        description = null,
        categoryId = categoryId,
        imageUrl = null,
        isAvailable = true,
        skus = listOf(Sku("$id-sku", "R", 50.0, isDefault = true)),
        hasOptionGroups = needsSheet,
    )

    private fun detail(id: String) = ProductDetail(id, id, null, null, true, TestData.milkTea.skus, TestData.milkTea.optionGroups)

    private fun TestScope.viewModel(): MenuViewModel {
        val viewModel = MenuViewModel(menu, config, session, AddToCartUseCase(session, rules), rules)
        // state is WhileSubscribed: keep a subscriber like the screen does.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        return viewModel
    }

    private fun stubMenu(delayMs: Long = 0) {
        coEvery { menu.categories(any()) } coAnswers {
            delay(delayMs)
            AppResult.Success(listOf(coffee, bakery))
        }
        coEvery { menu.products(any()) } coAnswers {
            delay(delayMs)
            AppResult.Success(listOf(latte, tea, croissant))
        }
    }

    @Test
    fun `categories and products load in parallel`() = runTest(dispatcher) {
        stubMenu(delayMs = 1_000)
        val viewModel = viewModel()

        advanceTimeBy(1_001) // one request's worth of time, not two
        runCurrent()

        assertFalse(viewModel.state.value.loading)
        assertEquals(3, viewModel.state.value.products.size)
    }

    @Test
    fun `retry while loading does not start a second load`() = runTest(dispatcher) {
        stubMenu(delayMs = 1_000)
        val viewModel = viewModel()
        runCurrent()

        viewModel.onEvent(MenuEvent.Retry)
        advanceUntilIdle()

        coVerify(exactly = 1) { menu.products(any()) }
    }

    @Test
    fun `cart changes keep the same product list instance`() = runTest(dispatcher) {
        stubMenu()
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onEvent(MenuEvent.SelectCategory(coffee.id))
        runCurrent()
        val products = viewModel.state.value.products
        assertEquals(listOf(latte, tea), products)

        viewModel.onEvent(MenuEvent.ProductTapped(croissant)) // added straight to the cart
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.cart.items.size)
        assertSame(products, viewModel.state.value.products)
    }

    @Test
    fun `cards learn how many of each product are in the cart`() = runTest(dispatcher) {
        stubMenu()
        val viewModel = viewModel()
        advanceUntilIdle()

        repeat(2) { viewModel.onEvent(MenuEvent.ProductTapped(croissant)) }
        advanceUntilIdle()

        assertEquals(mapOf(croissant.id to 2), viewModel.state.value.cartQuantities)
        assertEquals(null, viewModel.state.value.cartQuantities[latte.id])
    }

    @Test
    fun `a late detail for a dismissed product never fills another product's sheet`() = runTest(dispatcher) {
        stubMenu()
        coEvery { menu.productDetail(latte.id) } coAnswers {
            delay(2_000) // slow
            AppResult.Success(detail(latte.id))
        }
        coEvery { menu.productDetail(tea.id) } coAnswers {
            delay(5_000)
            AppResult.Success(detail(tea.id))
        }
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onEvent(MenuEvent.ProductTapped(latte))
        runCurrent()
        viewModel.onEvent(MenuEvent.DismissSheet)
        viewModel.onEvent(MenuEvent.ProductTapped(tea))
        advanceTimeBy(3_000) // latte's answer would have arrived by now
        runCurrent()

        val sheet = viewModel.sheet.value
        assertEquals(tea.id, sheet?.productId)
        assertTrue(sheet?.loading == true)
        assertNull(sheet?.product)

        advanceUntilIdle()
        assertEquals(tea.id, viewModel.sheet.value?.product?.id)
    }

    @Test
    fun `an item sold out since the load shows as sold out, and comes back when restocked`() = runTest(dispatcher) {
        stubMenu()
        val viewModel = viewModel()
        advanceUntilIdle()

        unavailable.value = setOf(croissant.id)
        runCurrent()
        assertFalse(viewModel.state.value.products.first { it.id == croissant.id }.isAvailable)
        assertTrue(viewModel.state.value.products.first { it.id == latte.id }.isAvailable)
        assertEquals(setOf(croissant.id), viewModel.state.value.unavailableProductIds)

        unavailable.value = emptySet()
        runCurrent()
        assertTrue(viewModel.state.value.products.first { it.id == croissant.id }.isAvailable)
    }

    @Test
    fun `the open option sheet says so when its item sells out, and won't add it`() = runTest(dispatcher) {
        stubMenu()
        coEvery { menu.productDetail(latte.id) } returns AppResult.Success(detail(latte.id))
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onEvent(MenuEvent.ProductTapped(latte))
        advanceUntilIdle()

        unavailable.value = setOf(latte.id)
        runCurrent()
        viewModel.onEvent(MenuEvent.ConfirmSheet)

        assertEquals(AppError.ProductSoldOut, viewModel.sheet.value?.error)
        assertTrue(session.session.value.cart.isEmpty)
    }

    @Test
    fun `menu banners come from the kiosk display settings`() = runTest(dispatcher) {
        stubMenu()
        val viewModel = viewModel()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.banners.images.isEmpty())

        val banners = KioskDisplay.Banners(slideSeconds = 4, images = listOf(DisplayImage("b1", "https://cdn/b1.webp")))
        configFlow.value = kioskConfig(KioskDisplay(banners = banners))
        runCurrent()

        assertEquals(banners, viewModel.state.value.banners)
    }

    private fun kioskConfig(display: KioskDisplay) = KioskConfig(
        terminalId = "t",
        terminalCode = "K1",
        terminalName = "Kiosk",
        branch = BranchInfo("b", "br", "Siam", "Cafe", null, "#FF5E00", "", null, null, null),
        allowDineIn = true,
        allowTakeaway = false,
        isOpen = true,
        closedReason = null,
        taxRate = 7.0,
        taxIncluded = true,
        serviceChargeRate = 0.0,
        cashEnabled = true,
        promptPayEnabled = false,
        receiptSettings = null,
        receiptPrinter = null,
        display = display,
    )
}
