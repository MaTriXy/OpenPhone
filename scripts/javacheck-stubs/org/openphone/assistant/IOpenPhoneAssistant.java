// Compile-check stub standing in for the AIDL-generated interface.
package org.openphone.assistant;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

public interface IOpenPhoneAssistant extends IInterface {
    String getStatus() throws RemoteException;

    String startTask(String taskRequestJson) throws RemoteException;

    String getScreenContext(String taskId) throws RemoteException;

    String executeAction(String taskId, String actionRequestJson) throws RemoteException;

    String confirmAction(String pendingActionId, boolean approved) throws RemoteException;

    String evaluateCapability(String capabilityId) throws RemoteException;

    String getAuditLog(int maxEvents) throws RemoteException;

    abstract class Stub extends Binder implements IOpenPhoneAssistant {
        @Override
        public IBinder asBinder() {
            return this;
        }
    }
}
