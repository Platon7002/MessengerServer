package server

interface RequestHandler {
    fun handle(request: HttpRequest): HttpResponse
}
