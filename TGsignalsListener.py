import sys
import time
import re
import random
import configparser

from telethon import utils, events, TelegramClient, ConnectionMode
from telethon.tl.functions.channels import GetFullChannelRequest
from telethon.tl.types import InputChannel, PeerChat
from telethon.tl.functions.contacts import ResolveUsernameRequest
from telethon.tl.functions.messages import CheckChatInviteRequest
from telethon.extensions import markdown

class RegBlock(object):
    def __init__(self, name):
        self.name = name
        self.reg_has = None
        self.reg_skip = None
        self.reg_coin = None
        self.reg_price_from = None
        self.reg_price_to = None
        self.reg_price_target = None

def getFirstMatch(regex, txt):
    matches = re.finditer(regex, txt, re.UNICODE | re.IGNORECASE)
    for matchNum, match in enumerate(matches):
        for groupNum in range(0, len(match.groups())):
            return match.group(1)
    return ''

def textCheck(txt, date, client):
    if (txt is None) or (txt == ''):
        return

    global signal_expr
    non_bmp_map = dict.fromkeys(range(0x10000, sys.maxunicode + 1), 0xfffd)
    txt = txt.translate(non_bmp_map)

    i = 0
    while i < len(signal_expr):
        hasmatch = getFirstMatch(signal_expr[i].reg_has, txt)
        skipmatch = getFirstMatch(signal_expr[i].reg_skip, txt)
        if hasmatch and not skipmatch:
            #print(txt)
            coin = getFirstMatch(signal_expr[i].reg_coin, txt)
            price_from = getFirstMatch(signal_expr[i].reg_price_from, txt)
            price_to = getFirstMatch(signal_expr[i].reg_price_to, txt)
            price_target = getFirstMatch(signal_expr[i].reg_price_target, txt)
            print(date + ";" + coin + ";" + price_from + ";" + price_to + ";" + price_target)
        i = i + 1
    
    return

def checkCurchan(channame, client):
    try:
        int_chan = int(channame)
    except:
        int_chan = 0

    if int_chan != 0:
        input_channel =  client(GetFullChannelRequest(int_chan))
        channel_id = input_channel.full_chat.id
    else:
        response = client.invoke(ResolveUsernameRequest(channame))
        channel_id = response.peer.channel_id

    for msg in client.get_messages(channel_id, limit=10):
        if (msg != '') and not (msg is None):
            if not hasattr(msg, 'text'):
                msg.text = None
            if msg.text is None and hasattr(msg, 'entities'):
                msg.text = markdown.unparse(msg.message, msg.entities or [])
            if msg.text is None and msg.message:
                msg.text = msg.message
            textCheck(msg.text, str(msg.date), client)

    return channel_id

def main():
    #sys.argv[1]

    global signal_expr
    signal_expr = []

    config = configparser.ConfigParser()
    config.read('signals_listener.ini')
    session_name = config['main']['session_fname']
    api_id = config['main']['api_id']
    api_hash = config['main']['api_hash']
    phone = config['main']['phone_number']

    chancount = int(config['channels']['count'])
    channames = []

    chan_index = 1
    while chan_index <= chancount:
        cname = config['channels']['name' + str(chan_index)]
        channames.append(cname)
        expr_b = RegBlock(cname)
        expr_b.reg_has = config['channels']['signal_has_' + str(chan_index)]
        expr_b.reg_skip = config['channels']['signal_skip_' + str(chan_index)]
        expr_b.reg_coin = config['channels']['signal_coin_' + str(chan_index)]
        expr_b.reg_price_from = config['channels']['signal_price_from_' + str(chan_index)]
        expr_b.reg_price_to = config['channels']['signal_price_to_' + str(chan_index)]
        expr_b.reg_price_target = config['channels']['signal_price_target_' + str(chan_index)]
        signal_expr.append(expr_b)
        chan_index = chan_index + 1

    client = TelegramClient(
        session_name,
        api_id=api_id,
        api_hash=api_hash,
        connection_mode=ConnectionMode.TCP_ABRIDGED,
        proxy=None,
        update_workers=1,
        spawn_read_thread=True
    )

    print('Connecting to Telegram servers...')
    if not client.connect():
        print('Initial connection failed. Retrying...')
        if not client.connect():
            print('Could not connect to Telegram servers.')
            return

    if not client.is_user_authorized():
        client.send_code_request(phone)
        client.sign_in(phone, input('Enter the code ('+phone+'): '))

    print(client.session.server_address)   # Successfull

    chan_index = 0
    while chan_index < len(channames):
        channames[chan_index] = checkCurchan(channames[chan_index], client)
        time.sleep(1)
        chan_index = chan_index + 1

    print("Channels: " + str(channames))

    @client.on(events.NewMessage(chats=channames, incoming=True))
    def normal_handler(event):
        textCheck(event.text, str(event.date), client)

    print('Listening for new messages...')

    while True:
        time.sleep(0.1)

if __name__ == '__main__':
    main()
