///////////////////////////////////////////////////////////////////////////////
// FILE:       LeicaDMIScopeInterface.h
// PROJECT:    MicroManager
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION: Interface to the microscope.  Communicates with the scope and updates
//              the abstract model
//   
// COPYRIGHT:     100xImaging, Inc., 2008
// LICENSE:        
//                This library is free software; you can redistribute it and/or
//                modify it under the terms of the GNU Lesser General Public
//                License as published by the Free Software Foundation.
//                
//                You should have received a copy of the GNU Lesser General Public
//                License along with the source distribution; if not, write to
//                the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
//                Boston, MA  02111-1307  USA
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.  

#ifndef _LEICASCOPEINTERFACE_H_
#define _LEICASCOPEINTERFACE_H_

#include "LeicaDMIModel.h"
#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/DeviceThreads.h"
#include <string>
#include <vector>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
class LeicaScopeInterface
{
   friend class LeicaScope;
   friend class LeicaMonitoringThread;

   public:
      LeicaScopeInterface();
      ~LeicaScopeInterface();

      int Initialize(MM::Device& device, MM::Core& core);

      MM::MMTime GetTimeOutTime(){ return timeOutTime_;}
      void SetTimeOutTime(MM::MMTime timeOutTime) { timeOutTime_ = timeOutTime;}

      int GetStandInfo(MM::Device& device, MM::Core& core);
     
      int SetTLShutterPosition(MM::Device& device, MM::Core& core, int position);
      int SetReflectorTurretPosition(MM::Device& device, MM::Core& core, int position);

      LeicaMonitoringThread* monitoringThread_;

      // access functions for device info from microscope model (use lock)
      /*
      int GetModelPosition(MM::Device& device, MM::Core& core, LeicaUByte devId, LeicaLong& position);
      int GetModelMaxPosition(MM::Device& device, MM::Core& core, LeicaUByte devId, LeicaLong& maxPosition);
      int GetModelStatus(MM::Device& device, MM::Core& core, LeicaUByte devId, LeicaULong& status);
      int GetModelPresent(MM::Device& device, MM::Core& core, LeicaUByte devId, bool &present);
      int GetModelBusy(MM::Device& device, MM::Core& core, LeicaUByte devId, bool &busy);
      */
      // a few classes can set devices in the microscope model to be busy
      //int SetModelBusy( LeicaUByte devId, bool busy);

      std::string port_;
      bool portInitialized_;
      static const int RCV_BUF_LENGTH = 1024;
      unsigned char rcvBuf_[RCV_BUF_LENGTH];

   private:
      void ClearRcvBuf();
      int ClearPort(MM::Device& device, MM::Core& core);
      //void SetPort(const char* port) {port_ = port; portInitialized_ = true;}
      //int GetVersion(MM::Device& device, MM::Core& core);
      //int FindDevices(MM::Device& device, MM::Core& core);

      // These are used by MonitoringThread to set values in our microscope model
      /*
      int SetModelPosition(LeicaUByte devId, LeicaLong position);
      int SetUpperHardwareStop(LeicaUByte devId, LeicaLong position);
      int SetLowerHardwareStop(LeicaUByte devId, LeicaLong position);
      int SetModelStatus(LeicaUByte devId, LeicaULong status);
      */

      // Helper function for GetAnswer
      bool signatureFound(unsigned char* answer, unsigned char* signature, unsigned long signatureStart, unsigned long signatureLength);

      // functions used only from initialize, they set values in our abstract microscope 'deviceInfo_', based on information obtained from the microscope
      //int GetPosition(MM::Device& device, MM::Core& core, LeicaUByte commandGroup, LeicaUByte devId, LeicaLong& position);
      // int GetMaxPosition(MM::Device& device, MM::Core& core, LeicaUByte commandGroup, LeicaUByte devId, LeicaLong& position);
      // int GetDeviceScalings(MM::Device& device, MM::Core& core, LeicaUByte commandGroup, LeicaUByte devId, LeicaDeviceInfo& deviceInfo);
      // int GetScalingTable(MM::Device& device, MM::Core& core, LeicaUByte commandGroup, LeicaUByte devId, LeicaDeviceInfo& deviceInfo, std::string unit);
      // int GetMeasuringOrigin(MM::Device& device, MM::Core& core, LeicaUByte commandGroup, LeicaUByte devId, LeicaDeviceInfo& deviceInfo);
      // int GetPermanentParameter(MM::Device& device, MM::Core& core, LeicaUShort descriptor, LeicaByte entry, LeicaUByte& dataType, unsigned char* data, unsigned char& dataLength);
      // int GetReflectorLabels(MM::Device& device, MM::Core& core);
      // int GetObjectiveLabels(MM::Device& device, MM::Core& core);
      // int GetTubeLensLabels(MM::Device& device, MM::Core& core);
      // int GetSidePortLabels(MM::Device& device, MM::Core& core);
      // int GetCondenserLabels(MM::Device& device, MM::Core& core);

      MM::MMTime timeOutTime_;
      //static LeicaDeviceInfo deviceInfo_[MAXNUMBERDEVICES];
      // std::vector<LeicaUByte > availableDevices_;
      //static std::vector<LeicaUByte > commandGroup_; // relates device to commandgroup, initialized in constructor
      std::string version_;
      bool initialized_;
};

class LeicaMessageParser{
   public:
      LeicaMessageParser(unsigned char* inputStream, long inputStreamLength);
      ~LeicaMessageParser(){};
      int GetNextMessage(unsigned char* nextMessage, int& nextMessageLength);
      static const int messageMaxLength_ = 64;

   private:
      unsigned char* inputStream_;
      long inputStreamLength_;
      long index_;
};

class LeicaMonitoringThread : public MMDeviceThreadBase
{
   public:
      LeicaMonitoringThread(MM::Device& device, MM::Core& core, std::string port); 
      ~LeicaMonitoringThread(); 
      //MM_THREAD_FUNC_DECL svc(void *arg);
      int svc();
      int open (void*) { return 0;}
      int close(unsigned long) {return 0;}

      void Start();
      void Stop() {stop_ = true;}
      //void wait() {MM_THREAD_JOIN(thread_);}

   private:
      std::string port_;
      void interpretMessage(unsigned char* message);
      MM::Device& device_;
      MM::Core& core_;
      bool stop_;
      long intervalUs_;
      LeicaMonitoringThread& operator=(LeicaMonitoringThread& ) {assert(false); return *this;}
};

#endif
