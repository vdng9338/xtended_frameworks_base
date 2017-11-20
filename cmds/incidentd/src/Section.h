/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef SECTIONS_H
#define SECTIONS_H

#include "Reporter.h"

#include <stdarg.h>
#include <utils/String8.h>
#include <utils/String16.h>
#include <utils/Vector.h>

using namespace android;

const int64_t REMOTE_CALL_TIMEOUT_MS = 10 * 1000; // 10 seconds

/**
 * Base class for sections
 */
class Section
{
public:
    const int id;
    const int64_t timeoutMs; // each section must have a timeout
    String8 name;

    Section(int id, const int64_t timeoutMs = REMOTE_CALL_TIMEOUT_MS);
    virtual ~Section();

    virtual status_t Execute(ReportRequestSet* requests) const = 0;
};

/**
 * Section that generates incident headers.
 */
class HeaderSection : public Section
{
public:
    HeaderSection();
    virtual ~HeaderSection();

    virtual status_t Execute(ReportRequestSet* requests) const;
};

/**
 * Section that reads in a file.
 */
class FileSection : public Section
{
public:
    FileSection(int id, const char* filename, const int64_t timeoutMs = 5000 /* 5 seconds */);
    virtual ~FileSection();

    virtual status_t Execute(ReportRequestSet* requests) const;

private:
    const char* mFilename;
    bool mIsSysfs; // sysfs files are pollable but return POLLERR by default, handle it separately
};

/**
 * Base class for sections that call a command that might need a timeout.
 */
class WorkerThreadSection : public Section
{
public:
    WorkerThreadSection(int id);
    virtual ~WorkerThreadSection();

    virtual status_t Execute(ReportRequestSet* requests) const;

    virtual status_t BlockingCall(int pipeWriteFd) const = 0;
};

/**
 * Section that forks and execs a command, and puts stdout as the section.
 */
class CommandSection : public Section
{
public:
    CommandSection(int id, const int64_t timeoutMs, const char* command, ...);

    CommandSection(int id, const char* command, ...);

    virtual ~CommandSection();

    virtual status_t Execute(ReportRequestSet* requests) const;

private:
    const char** mCommand;

    void init(const char* command, va_list args);
};

/**
 * Section that calls dumpsys on a system service.
 */
class DumpsysSection : public WorkerThreadSection
{
public:
    DumpsysSection(int id, const char* service, ...);
    virtual ~DumpsysSection();

    virtual status_t BlockingCall(int pipeWriteFd) const;

private:
    String16 mService;
    Vector<String16> mArgs;
};

#endif // SECTIONS_H

