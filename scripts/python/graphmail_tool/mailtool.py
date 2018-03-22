from lib.mailtojson import MailJson
import argparse

from kvdn_client.kvdn_client import kvdn_client
import numpy as np
import matplotlib.pyplot as plt

_baseurl = 'http://localhost:6502'

import os, json, sys


def plotDegDist():
    k = kvdn_client(baseurl=_baseurl)
    degdistdata = json.loads(k.get("GRAPH/METRICS", "degDist"))
    #
    npX = np.array(degdistdata['X'])
    npY = np.array(degdistdata['Y'])

    plt.yscale('log')
    plt.xscale('log')
    fig = plt.plot(npX, npY)
    plt.show()


def processDir(dirname):
    k = kvdn_client(baseurl=_baseurl)
    for root, dirs, files in os.walk(dirname):
        for file in files:
            path = os.path.join(root, file)
            print("processing " + path)
            with open(path, 'r') as content_file:
                content = content_file.read()
                result = processMailFile(content)
                if result:
                    print(type(result))
                    k.submit_cas("GRAPH/MAIL", result)


def processMailFile(content):
    try:
        mj = MailJson(content)
        mj.parse()
        data = mj.get_data()
        return (json.dumps(data, encoding=data.get("encoding")))
    except Exception, inst:
        print "ERR: %s" % inst


if __name__ == "__main__":
    usage = "usage: %prog [options]"
    parser = argparse.ArgumentParser(description='mail tool')
    parser.add_argument("--import_maildir", type=str, default=False, help="the maildir to process")
    parser.add_argument("--plot", action='store_true', default=False, help="show plot of degree distribution")

    args = parser.parse_args()
    if (args.import_maildir):
        processDir(args.import_maildir)
    if (args.plot):
        plotDegDist()
