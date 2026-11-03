package com.pos.kiosk.data.di

import com.pos.kiosk.core.printer.EscPosLanPrinter
import com.pos.kiosk.core.printer.ReceiptPrinter
import com.pos.kiosk.data.repository.AuthRepositoryImpl
import com.pos.kiosk.data.repository.ConfigRepositoryImpl
import com.pos.kiosk.data.repository.InMemoryOrderSessionRepository
import com.pos.kiosk.data.repository.MemberRepositoryImpl
import com.pos.kiosk.data.repository.MenuRepositoryImpl
import com.pos.kiosk.data.repository.OrderRepositoryImpl
import com.pos.kiosk.data.repository.PaymentRepositoryImpl
import com.pos.kiosk.domain.repository.AuthRepository
import com.pos.kiosk.domain.repository.ConfigRepository
import com.pos.kiosk.domain.repository.MemberRepository
import com.pos.kiosk.domain.repository.MenuRepository
import com.pos.kiosk.domain.repository.OrderRepository
import com.pos.kiosk.domain.repository.OrderSessionRepository
import com.pos.kiosk.domain.repository.PaymentRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun authRepository(impl: AuthRepositoryImpl): AuthRepository
    @Binds abstract fun configRepository(impl: ConfigRepositoryImpl): ConfigRepository
    @Binds abstract fun menuRepository(impl: MenuRepositoryImpl): MenuRepository
    @Binds abstract fun memberRepository(impl: MemberRepositoryImpl): MemberRepository
    @Binds abstract fun orderSessionRepository(impl: InMemoryOrderSessionRepository): OrderSessionRepository
    @Binds abstract fun orderRepository(impl: OrderRepositoryImpl): OrderRepository
    @Binds abstract fun paymentRepository(impl: PaymentRepositoryImpl): PaymentRepository
    @Binds abstract fun receiptPrinter(impl: EscPosLanPrinter): ReceiptPrinter
}
