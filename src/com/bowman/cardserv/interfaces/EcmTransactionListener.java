package com.bowman.cardserv.interfaces;

import com.bowman.cardserv.session.EcmTransaction;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: 2007-aug-29
 * Time: 12:52:58
 */
public interface EcmTransactionListener {

  void transactionCompleted(EcmTransaction transaction, ProxySession source);

}
