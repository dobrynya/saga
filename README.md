Simple Saga pattern implementation
====
This code provides sample decision to coordinate complex non-transactional processes and roll back different parts in case of any failure.

Consider the following case: a system would like to send an invoice to a client. 

```Scala
import shapeless.HNil
import ru.dimitrius.SagaBuilder.saga

val persistInvoiceAndSendEmail: Future[Email :: Person :: HNil] = saga
  .part[Person](PersonService.addInvoice(person, invoice), p => PersonService.deleteInvoice(p, invoice))
  .part[Email](EmailService.sendInvoice(person.email, invoice), letter => EmailService.sendExcuse(letter.email, EmailService.createExcuse(letter)))
  .run
  
persistInvoiceAndSendEmail.onComplete {
  case Success(email :: person :: HNil) =>
    logger.debug("There you can manage process results")
  case Failure(SagaFailed(message, _)) =>
    logger.error(s"One saga part has been failed due to $message")
}
```
