package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemWriteRepository

internal interface LoginDefaultUserUseCase {
    operator fun invoke()
}

internal class LoginDefaultUserUseCaseImpl(
    private val repository: ProblemWriteRepository,
) : LoginDefaultUserUseCase {
    override fun invoke() {
        repository.loginDefaultUser()
    }
}
