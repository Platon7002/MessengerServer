package service

class ServiceException(val status: Int, message: String) : Exception(message)
