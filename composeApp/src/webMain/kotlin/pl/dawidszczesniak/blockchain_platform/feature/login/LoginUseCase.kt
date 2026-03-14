package pl.dawidszczesniak.blockchain_platform.feature.login

import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemRepository

interface LoginUseCase {
    suspend operator fun invoke()
}

class LoginUseCaseImpl(
    private val repository: ProblemRepository,
) : LoginUseCase {
    override suspend fun invoke() {
        repository.login()
    }
}
